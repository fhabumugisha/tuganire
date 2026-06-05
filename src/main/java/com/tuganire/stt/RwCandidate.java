package com.tuganire.stt;

/**
 * One model's cleaned Kinyarwanda transcript, shown as an A/B candidate the user can pick.
 *
 * @param modelId
 *            the model id that produced this candidate (e.g. {@code "gpt-5.5"})
 * @param label
 *            human-readable model label for display (e.g. {@code "GPT-5.5 (OpenAI)"})
 * @param text
 *            the cleaned Kinyarwanda transcript
 */
public record RwCandidate(String modelId, String label, String text) {
}
