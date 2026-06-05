package com.tuganire.conversation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for {@code PUT /api/v1/settings/temperature}.
 *
 * <p>
 * Out-of-range values are rejected with HTTP 400 by the global validation handler before they ever reach the service
 * layer. The server-side clamp in {@code LlmSettings.setTemperature} is kept as defence-in-depth.
 *
 * @param temperature
 *            requested temperature; must be in {@code [0.0, 1.0]}
 */
public record TemperatureRequest(
        @NotNull(message = "La température est obligatoire") @DecimalMin(value = "0.0", message = "La température doit être entre 0.0 et 1.0") @DecimalMax(value = "1.0", message = "La température doit être entre 0.0 et 1.0") Double temperature) {
}
