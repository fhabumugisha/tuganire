package com.tuganire.conversation;

import com.tuganire.translation.TranslationResponse;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Orchestrates the full audio pipeline: STT → translate → TTS → audio store.
 *
 * <p>
 * Used by both the non-streaming REST endpoint ({@code POST /api/v1/audio/translate}) and the streaming WebSocket
 * handler. The two call styles share the same internal pipeline; the streaming variant additionally emits
 * {@link ConversationEvent} values to a caller-supplied sink.
 */
public interface ConversationService {

    /**
     * Result of a single audio-translation pipeline run.
     *
     * @param transcript
     *            the text transcribed from the input audio
     * @param translation
     *            the full translation response (includes corrections, flags, etc.)
     * @param audioUrl
     *            relative URL under which the synthesised audio can be fetched (e.g. {@code /api/v1/audio/{id}.mp3})
     * @param durationMs
     *            wall-clock time the pipeline took, in milliseconds
     */
    record AudioTranslationResult(String transcript, TranslationResponse translation, String audioUrl,
            long durationMs) {
    }

    /**
     * Runs the non-streaming audio pipeline.
     *
     * <p>
     * The pipeline executes in three sequential stages (STT → translate → TTS), then stores the resulting MP3 bytes in
     * Redis with a 5-minute TTL and returns an {@link AudioTranslationResult} carrying the transcript, translation and
     * a short-lived audio URL.
     *
     * @param audio
     *            raw audio bytes (WAV, MP3, M4A, or WebM); non-null, non-empty
     * @param srcLang
     *            spoken language in the input audio
     * @param tgtLang
     *            target language for translation and TTS
     * @param sessionId
     *            caller's session identifier used for correlation
     * @return the fully-populated result; never {@code null}
     * @throws com.tuganire.shared.exception.BusinessException
     *             if any stage fails and the error is non-retryable
     */
    AudioTranslationResult processAudio(byte[] audio, Locale srcLang, Locale tgtLang, String sessionId);

    /**
     * Runs the streaming audio pipeline, emitting {@link ConversationEvent} values to {@code sink} as each stage
     * completes.
     *
     * <p>
     * The sink receives events in order:
     * <ol>
     * <li>{@link ConversationEvent.FinalTranscript} — after STT completes
     * <li>{@link ConversationEvent.TranslationReady} — after translation completes
     * <li>{@link ConversationEvent.AudioReady} — after TTS + store complete
     * </ol>
     * On any failure, an {@link ConversationEvent.ErrorOccurred} event is emitted and no further events follow.
     *
     * @param audio
     *            raw audio bytes; non-null, non-empty
     * @param srcLang
     *            spoken language in the input audio
     * @param tgtLang
     *            target language for translation and TTS
     * @param sessionId
     *            caller's session identifier
     * @param sink
     *            consumer that receives each event; must be thread-safe
     */
    void processAudioStreaming(byte[] audio, Locale srcLang, Locale tgtLang, String sessionId,
            Consumer<ConversationEvent> sink);
}
