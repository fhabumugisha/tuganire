package com.tuganire.conversation;

import com.tuganire.translation.TranslationResponse;
import java.util.List;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Maps each {@link ConversationEvent} record to the JSON shape documented in ARCHI.md section 6.
 *
 * <p>
 * Field names and {@code type} string values are fixed by the client/server protocol; do not rename them without
 * updating ARCHI.md and the Android client.
 *
 * <p>
 * Note: {@link ConversationEvent.PartialTranscript} streaming is optional for the MVP — the STT provider (Whisper) does
 * not support incremental results, so only {@code FINAL_TRANSCRIPT} is emitted. When a partial-capable STT provider is
 * added, wire {@link ConversationEvent.PartialTranscript} here and emit it before the final transcript.
 */
public final class ConversationEventJson {

    private static final String FIELD_TYPE = "type";
    private static final String FIELD_SESSION_ID = "sessionId";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_LANG = "lang";
    private static final String FIELD_FROM_GOLDEN_DICT = "fromGoldenDict";
    private static final String FIELD_APPLIED_CORRECTIONS = "appliedCorrections";
    private static final String FIELD_AUDIO_URL = "audioUrl";
    private static final String FIELD_DURATION_MS = "durationMs";
    private static final String FIELD_CODE = "code";
    private static final String FIELD_MESSAGE = "message";

    /** Protocol type strings — uppercase as defined in ARCHI.md section 6. */
    private static final String TYPE_PARTIAL_TRANSCRIPT = "PARTIAL_TRANSCRIPT";
    private static final String TYPE_FINAL_TRANSCRIPT = "FINAL_TRANSCRIPT";
    private static final String TYPE_TRANSLATION_READY = "TRANSLATION_READY";
    private static final String TYPE_AUDIO_READY = "AUDIO_READY";
    private static final String TYPE_ERROR = "ERROR";

    private final ObjectMapper mapper;

    ConversationEventJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Serialises a {@link ConversationEvent} to a JSON string matching the protocol shape.
     *
     * @param event
     *            the event to serialise; must be one of the permitted subtypes
     * @param translationResponse
     *            the full translation result; required only for {@link ConversationEvent.TranslationReady} to populate
     *            {@code fromGoldenDict} and {@code appliedCorrections}; {@code null} for all other event types
     * @return a JSON string ready to send as a WebSocket {@code TextMessage}
     */
    String toJson(ConversationEvent event, TranslationResponse translationResponse) {
        try {
            ObjectNode node = switch (event) {
                case ConversationEvent.PartialTranscript p -> buildPartialTranscript(p);
                case ConversationEvent.FinalTranscript f -> buildFinalTranscript(f);
                case ConversationEvent.TranslationReady t -> buildTranslationReady(t, translationResponse);
                case ConversationEvent.AudioReady a -> buildAudioReady(a);
                case ConversationEvent.ErrorOccurred e -> buildError(e);
                // SpeechStarted has no dedicated protocol message; callers should not pass it here
                case ConversationEvent.SpeechStarted ignored -> mapper.createObjectNode();
            };
            return mapper.writeValueAsString(node);
        } catch (Exception ex) {
            // Fall back to a minimal error payload so the client always receives valid JSON
            return buildFallbackError(event.getClass().getSimpleName(), ex.getMessage());
        }
    }

    private ObjectNode buildPartialTranscript(ConversationEvent.PartialTranscript p) {
        ObjectNode node = mapper.createObjectNode();
        node.put(FIELD_TYPE, TYPE_PARTIAL_TRANSCRIPT);
        node.put(FIELD_SESSION_ID, p.sessionId());
        node.put(FIELD_TEXT, p.text());
        return node;
    }

    private ObjectNode buildFinalTranscript(ConversationEvent.FinalTranscript f) {
        ObjectNode node = mapper.createObjectNode();
        node.put(FIELD_TYPE, TYPE_FINAL_TRANSCRIPT);
        node.put(FIELD_SESSION_ID, f.sessionId());
        node.put(FIELD_TEXT, f.text());
        node.put(FIELD_LANG, f.language().getLanguage());
        return node;
    }

    private ObjectNode buildTranslationReady(ConversationEvent.TranslationReady t,
            TranslationResponse translationResponse) {
        ObjectNode node = mapper.createObjectNode();
        node.put(FIELD_TYPE, TYPE_TRANSLATION_READY);
        node.put(FIELD_SESSION_ID, t.sessionId());
        node.put(FIELD_TEXT, t.text());
        node.put(FIELD_LANG, t.targetLang().getLanguage());
        if (translationResponse != null) {
            node.put(FIELD_FROM_GOLDEN_DICT, translationResponse.fromGoldenDictionary());
            List<String> corrections = translationResponse.appliedCorrections();
            var arr = mapper.createArrayNode();
            if (corrections != null) {
                corrections.forEach(arr::add);
            }
            node.set(FIELD_APPLIED_CORRECTIONS, arr);
        } else {
            node.put(FIELD_FROM_GOLDEN_DICT, false);
            node.set(FIELD_APPLIED_CORRECTIONS, mapper.createArrayNode());
        }
        return node;
    }

    private ObjectNode buildAudioReady(ConversationEvent.AudioReady a) {
        ObjectNode node = mapper.createObjectNode();
        node.put(FIELD_TYPE, TYPE_AUDIO_READY);
        node.put(FIELD_SESSION_ID, a.sessionId());
        node.put(FIELD_AUDIO_URL, a.audioUrl());
        node.put(FIELD_DURATION_MS, a.durationMs());
        return node;
    }

    private ObjectNode buildError(ConversationEvent.ErrorOccurred e) {
        ObjectNode node = mapper.createObjectNode();
        node.put(FIELD_TYPE, TYPE_ERROR);
        node.put(FIELD_SESSION_ID, e.sessionId());
        node.put(FIELD_CODE, e.code());
        node.put(FIELD_MESSAGE, e.message());
        return node;
    }

    private String buildFallbackError(String context, String detail) {
        return """
                {"type":"ERROR","sessionId":"unknown","code":"SERIALIZATION_FAILED","message":"%s: %s"}
                """.formatted(context, detail == null ? "null" : detail.replace("\"", "'")).strip();
    }
}
