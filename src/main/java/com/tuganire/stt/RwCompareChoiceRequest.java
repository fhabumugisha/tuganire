package com.tuganire.stt;

import jakarta.validation.constraints.NotBlank;

/**
 * Request of {@code POST /api/v1/stt/compare-choice}: records which model the user preferred.
 *
 * @param chosenModel
 *            the model id the user picked; required
 * @param rejectedModel
 *            the model id the user did not pick; optional
 * @param sessionId
 *            the anonymous session id; optional
 */
public record RwCompareChoiceRequest(@NotBlank String chosenModel, String rejectedModel, String sessionId) {
}
