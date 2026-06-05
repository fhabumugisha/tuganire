package com.tuganire.stt;

/**
 * Aggregated A/B preference for one model, for the admin comparison stats.
 *
 * @param modelId
 *            the model id
 * @param label
 *            human-readable model label
 * @param votes
 *            number of times this model was preferred
 */
public record RwModelStat(String modelId, String label, long votes) {
}
