package com.tuganire.stt;

/**
 * Cleans up and improves a raw French transcript before it is translated.
 *
 * <p>
 * Used by the OpenAI French STT path: a Whisper transcription of a non-native speaker often contains grammar slips,
 * missing words, or phonetic approximations. This service asks an LLM to rewrite the sentence into correct, natural
 * French while preserving the speaker's intent — yielding a better source text for the downstream FR→RW translation.
 */
public interface FrenchCorrectionService {

    /**
     * Rewrites {@code rawTranscript} into correct, natural French.
     *
     * <p>
     * Best-effort: implementations must return the original text unchanged on any failure rather than throwing, so a
     * correction problem never blocks the transcription flow.
     *
     * @param rawTranscript
     *            the raw French transcript produced by the STT provider
     * @return the corrected French text, or the original input if correction was not possible
     */
    String correct(String rawTranscript);
}
