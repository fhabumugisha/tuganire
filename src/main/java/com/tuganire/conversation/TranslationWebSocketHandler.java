package com.tuganire.conversation;

import com.tuganire.translation.TranslationResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * WebSocket handler for {@code /ws/conversation/{sessionId}}.
 *
 * <p>
 * Binary protocol (client → server):
 * <ul>
 * <li>{@code 0x01} — audio PCM 16kHz mono chunk; appended to the per-session buffer
 * <li>{@code 0x02} — metadata JSON ({@code {"srcLang":"fr","tgtLang":"rw","endOfTurn":true}}); on {@code endOfTurn},
 * flushes the buffer through the full STT → translate → TTS pipeline
 * <li>{@code 0x03} — keepalive ping; answered with a text {@code "pong"} without starting the pipeline
 * </ul>
 *
 * <p>
 * Text protocol (server → client): JSON messages whose {@code type} field is one of {@code PARTIAL_TRANSCRIPT},
 * {@code FINAL_TRANSCRIPT}, {@code TRANSLATION_READY}, {@code AUDIO_READY}, or {@code ERROR} as documented in ARCHI.md
 * section 6.
 *
 * <p>
 * Thread-safety: each {@link WebSocketSession} is guarded by its own {@link ReentrantLock} (no {@code synchronized}).
 * The pipeline runs on a virtual-thread executor inside {@link ConversationService#processAudioStreaming}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TranslationWebSocketHandler extends BinaryWebSocketHandler {

    /** 1-byte type prefix for an audio PCM chunk. */
    private static final byte TYPE_AUDIO = 0x01;
    /** 1-byte type prefix for a metadata JSON frame signalling language and end-of-turn. */
    private static final byte TYPE_METADATA = 0x02;
    /** 1-byte type prefix for a keepalive ping. */
    private static final byte TYPE_KEEPALIVE = 0x03;

    private static final String ATTR_SESSION_ID = "sessionId";
    private static final String META_SRC_LANG = "srcLang";
    private static final String META_TGT_LANG = "tgtLang";
    private static final String META_END_OF_TURN = "endOfTurn";

    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    /**
     * Per-WebSocketSession state: accumulates audio bytes until end-of-turn.
     *
     * <p>
     * The buffer is a resizable byte array. We use a small record to bundle it with the send-lock for that session.
     */
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    /** Per-session state: audio buffer + send lock. */
    private static final class SessionState {

        /** Guards all {@link WebSocketSession#sendMessage} calls for this session. */
        final ReentrantLock sendLock = new ReentrantLock();

        /** Accumulated audio bytes; grown by appending each 0x01 chunk. */
        byte[] audioBuffer = new byte[0];

        /** Most-recently cached translation response; used to supply {@code fromGoldenDict} for JSON serialisation. */
        @Nullable
        volatile TranslationResponse lastTranslation;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String wsSessionId = session.getId();
        // The business sessionId is injected as an attribute by WebSocketConfig's HandshakeInterceptor
        Object sessionIdAttr = session.getAttributes().get(ATTR_SESSION_ID);
        String sessionId = (sessionIdAttr instanceof String s) ? s : wsSessionId;
        session.getAttributes().put(ATTR_SESSION_ID, sessionId);
        sessions.put(wsSessionId, new SessionState());
        log.info("WebSocket connected: wsSession={} sessionId={}", wsSessionId, sessionId);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        ByteBuffer payload = message.getPayload();
        if (!payload.hasRemaining()) {
            log.warn("Received empty binary frame: wsSession={}", session.getId());
            return;
        }
        byte type = payload.get();
        byte[] body = new byte[payload.remaining()];
        payload.get(body);

        switch (type) {
            case TYPE_AUDIO -> handleAudioChunk(session, body);
            case TYPE_METADATA -> handleMetadata(session, body);
            case TYPE_KEEPALIVE -> handleKeepalive(session);
            default -> {
                log.warn("Unknown binary frame type 0x{}: wsSession={}", Integer.toHexString(type & 0xFF),
                        session.getId());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String wsSessionId = session.getId();
        String sessionId = sessionId(session);
        log.error("WebSocket transport error: wsSession={} sessionId={}", wsSessionId, sessionId, exception);
        SessionState state = sessions.get(wsSessionId);
        if (state != null) {
            sendText(session, state, buildErrorJson(sessionId, "TRANSPORT_ERROR", exception.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String wsSessionId = session.getId();
        sessions.remove(wsSessionId);
        log.info("WebSocket closed: wsSession={} status={}", wsSessionId, status);
    }

    // --- private helpers ---

    private void handleAudioChunk(WebSocketSession session, byte[] chunk) {
        SessionState state = stateFor(session);
        if (state == null) {
            return;
        }
        // Append chunk to buffer (virtual-thread-safe: only one pipeline thread writes at a time per session,
        // but we guard with the sendLock to be explicit about the shared mutation)
        state.sendLock.lock();
        try {
            byte[] combined = new byte[state.audioBuffer.length + chunk.length];
            System.arraycopy(state.audioBuffer, 0, combined, 0, state.audioBuffer.length);
            System.arraycopy(chunk, 0, combined, state.audioBuffer.length, chunk.length);
            state.audioBuffer = combined;
        } finally {
            state.sendLock.unlock();
        }
        log.debug("Audio chunk appended: wsSession={} chunkBytes={} totalBytes={}", session.getId(), chunk.length,
                state.audioBuffer.length);
    }

    private void handleMetadata(WebSocketSession session, byte[] body) {
        String wsSessionId = session.getId();
        String sessionId = sessionId(session);
        SessionState state = stateFor(session);
        if (state == null) {
            return;
        }
        try {
            JsonNode meta = objectMapper.readTree(body);
            String srcLangCode = meta.path(META_SRC_LANG).asText("fr");
            String tgtLangCode = meta.path(META_TGT_LANG).asText("rw");
            boolean endOfTurn = meta.path(META_END_OF_TURN).asBoolean(false);

            if (!endOfTurn) {
                log.debug("Metadata frame without end-of-turn: wsSession={}", wsSessionId);
                return;
            }

            // Drain the audio buffer under lock to get a stable snapshot
            byte[] audioSnapshot;
            state.sendLock.lock();
            try {
                audioSnapshot = state.audioBuffer;
                state.audioBuffer = new byte[0];
            } finally {
                state.sendLock.unlock();
            }

            if (audioSnapshot.length == 0) {
                log.warn("End-of-turn with empty audio buffer: wsSession={}", wsSessionId);
                sendText(session, state,
                        buildErrorJson(sessionId, "EMPTY_AUDIO", "No audio received before end-of-turn"));
                return;
            }

            Locale srcLang = Locale.forLanguageTag(srcLangCode);
            Locale tgtLang = Locale.forLanguageTag(tgtLangCode);
            ConversationEventJson eventJson = new ConversationEventJson(objectMapper);

            log.info("Starting streaming pipeline: wsSession={} sessionId={} srcLang={} tgtLang={} audioBytes={}",
                    wsSessionId, sessionId, srcLang, tgtLang, audioSnapshot.length);

            // processAudioStreaming runs the pipeline on a virtual-thread executor (inside ConversationServiceImpl)
            // and calls the sink for each event — the sink may be invoked on any virtual thread.
            conversationService.processAudioStreaming(audioSnapshot, srcLang, tgtLang, sessionId, event -> {
                // Cache the TranslationResponse when it arrives so we can populate fromGoldenDict in the JSON
                if (event instanceof ConversationEvent.TranslationReady) {
                    // The last translation isn't available separately; ConversationServiceImpl emits only the event.
                    // We cannot populate fromGoldenDict here without it — emit with defaults (false / empty list).
                }
                String json = eventJson.toJson(event, state.lastTranslation);
                sendText(session, state, json);
            });

        } catch (JacksonException ex) {
            log.error("Failed to parse metadata frame: wsSession={}", wsSessionId, ex);
            sendText(session, state, buildErrorJson(sessionId, "METADATA_PARSE_ERROR", ex.getMessage()));
        }
    }

    private void handleKeepalive(WebSocketSession session) {
        SessionState state = stateFor(session);
        if (state == null) {
            return;
        }
        // Reply with a simple text pong; no pipeline activity
        sendText(session, state, "\"pong\"");
        log.debug("Keepalive pong sent: wsSession={}", session.getId());
    }

    /**
     * Sends a text message to the client. Guarded by the per-session {@link ReentrantLock} to prevent concurrent
     * {@link WebSocketSession#sendMessage} calls from racing on the same connection (virtual-thread safe, no
     * {@code synchronized}).
     */
    private void sendText(WebSocketSession session, SessionState state, String json) {
        state.sendLock.lock();
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException ex) {
            log.error("Failed to send TextMessage: wsSession={}", session.getId(), ex);
        } finally {
            state.sendLock.unlock();
        }
    }

    @Nullable
    private SessionState stateFor(WebSocketSession session) {
        SessionState state = sessions.get(session.getId());
        if (state == null) {
            log.warn("No SessionState found for wsSession={}", session.getId());
        }
        return state;
    }

    private String sessionId(WebSocketSession session) {
        Object attr = session.getAttributes().get(ATTR_SESSION_ID);
        return (attr instanceof String s) ? s : session.getId();
    }

    private String buildErrorJson(String sessionId, String code, @Nullable String message) {
        String safeMessage = (message == null ? "null" : message).replace("\"", "'");
        return """
                {"type":"ERROR","sessionId":"%s","code":"%s","message":"%s"}
                """.formatted(sessionId, code, safeMessage).strip();
    }
}
