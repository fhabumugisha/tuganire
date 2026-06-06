package com.tuganire.tts;

/**
 * Aggregated A/B preference for one TTS voice variant, for the admin comparison stats.
 *
 * @param variantId
 *            the voice variant id (e.g. {@code "mms-pauses"})
 * @param label
 *            human-readable voice label
 * @param votes
 *            number of times this voice was preferred
 */
public record TtsVoiceStat(String variantId, String label, long votes) {
}
