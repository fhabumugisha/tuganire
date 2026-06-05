package com.tuganire.stt;

/**
 * Cleans up a raw Kinyarwanda transcript (from MMS-ASR) using a chosen LLM.
 *
 * <p>
 * MMS-ASR is a CTC model: its output is all lowercase, has no punctuation, and contains occasional agreement/spelling
 * slips. This service asks an LLM to restore punctuation, capitalisation and obvious agreement while preserving the
 * speaker's exact words and intent. A deterministic pass then guarantees the orthographic rules an LLM may miss —
 * notably that {@code Imana} (God) is always capitalised, that the sentence starts with a capital, and that it ends
 * with terminal punctuation.
 *
 * <p>
 * The {@code modelId} is explicit so the A/B comparison feature can clean the same raw transcript with two different
 * models (e.g. {@code gpt-5.5} vs {@code claude-sonnet-4-6}) and let the user pick the better result.
 */
public interface KinyarwandaCorrectionService {

    /**
     * Rewrites {@code rawTranscript} into correctly punctuated and capitalised Kinyarwanda using {@code modelId}.
     *
     * <p>
     * Best-effort: returns a deterministically tidied version of the input on any LLM failure rather than throwing, so
     * a correction problem never blocks the transcription flow.
     *
     * @param rawTranscript
     *            the raw Kinyarwanda transcript produced by MMS-ASR
     * @param modelId
     *            the LLM model id to clean with (e.g. {@code "gpt-5.5"}, {@code "claude-sonnet-4-6"})
     * @return the corrected Kinyarwanda text
     */
    String correct(String rawTranscript, String modelId);
}
