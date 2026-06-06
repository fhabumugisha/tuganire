package com.tuganire.tts;

import jakarta.validation.constraints.NotBlank;

/**
 * Request of {@code POST /api/v1/tts/compare-vote}: records which Kinyarwanda voice the user preferred.
 *
 * @param chosenVariant
 *            the variant id the user picked; required
 * @param rejectedVariant
 *            the variant id the user did not pick; optional
 * @param sessionId
 *            the anonymous session id; optional
 */
public record TtsVoiceChoiceRequest(@NotBlank String chosenVariant, String rejectedVariant, String sessionId) {
}
