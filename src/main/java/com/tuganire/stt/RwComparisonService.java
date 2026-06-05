package com.tuganire.stt;

import java.util.List;

/**
 * Orchestrates the A/B comparison of Kinyarwanda transcript cleanups across two models, and records the user's
 * preference so {@code /admin} can show which model is best for Kinyarwanda.
 */
public interface RwComparisonService {

    /**
     * Cleans {@code rawTranscript} with each compared model (in parallel) and returns one candidate per model.
     *
     * @param rawTranscript
     *            the raw MMS-ASR Kinyarwanda transcript
     * @return one cleaned {@link RwCandidate} per compared model
     */
    List<RwCandidate> compare(String rawTranscript);

    /**
     * Records the user's preferred model for one comparison.
     *
     * @param chosenModel
     *            the model id the user preferred
     * @param rejectedModel
     *            the model id the user did not pick; may be {@code null}
     * @param sessionId
     *            the anonymous session id; may be {@code null}
     */
    void recordChoice(String chosenModel, String rejectedModel, String sessionId);

    /**
     * Returns the per-model preference totals for the admin comparison view.
     *
     * @return one {@link RwModelStat} per model that has received at least one vote
     */
    List<RwModelStat> stats();
}
