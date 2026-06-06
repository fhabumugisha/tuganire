package com.tuganire.tts;

import java.util.List;

/**
 * Orchestrates the A/B comparison of Kinyarwanda TTS voices (MMS with pauses vs OpenAI steered), and records the user's
 * preference so {@code /admin} can show which voice is best for Kinyarwanda.
 */
public interface TtsVoiceComparisonService {

    /**
     * Synthesises {@code text} with each compared voice variant (in parallel), stores each clip ephemerally, and
     * returns one candidate per variant in randomised order for a blind A/B test.
     *
     * @param text
     *            the Kinyarwanda text to speak
     * @param languageCode
     *            BCP-47 language code (e.g. {@code "rw"})
     * @return one {@link TtsVoiceCandidate} per compared variant, shuffled
     */
    List<TtsVoiceCandidate> compare(String text, String languageCode);

    /**
     * Records the user's preferred voice for one comparison.
     *
     * @param chosenVariant
     *            the variant id the user preferred
     * @param rejectedVariant
     *            the variant id the user did not pick; may be {@code null}
     * @param sessionId
     *            the anonymous session id; may be {@code null}
     */
    void recordChoice(String chosenVariant, String rejectedVariant, String sessionId);

    /**
     * Returns the per-variant preference totals for the admin comparison view.
     *
     * @return one {@link TtsVoiceStat} per variant that has received at least one vote
     */
    List<TtsVoiceStat> stats();
}
