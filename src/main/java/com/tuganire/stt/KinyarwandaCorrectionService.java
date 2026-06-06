package com.tuganire.stt;

/**
 * Deterministically tidies a raw Kinyarwanda transcript (from MMS-ASR).
 *
 * <p>
 * MMS-ASR is a CTC model: its output is all lowercase and has no punctuation. This service applies the instant,
 * deterministic orthographic rules an LLM may miss — {@code Imana} (God) is always capitalised, the sentence starts
 * with a capital, and it ends with terminal punctuation.
 *
 * <p>
 * The richer LLM-based correction (punctuation restoration, obvious agreement/spelling fixes) is performed once, by the
 * streaming translation pipeline ({@code StreamTranslationServiceImpl}), where it is streamed to the UI as it is
 * produced. Running an LLM correction here as well would be a redundant, blocking round-trip that delays the result.
 */
public interface KinyarwandaCorrectionService {

    /**
     * Applies the deterministic Kinyarwanda tidy rules to {@code raw}: capitalise {@code Imana}, capitalise the first
     * letter, and ensure terminal punctuation.
     *
     * @param raw
     *            the raw Kinyarwanda transcript produced by MMS-ASR
     * @return the tidied Kinyarwanda text, or an empty string for blank input
     */
    String tidy(String raw);
}
