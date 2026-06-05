package com.tuganire.conversation.dto;

/**
 * Response for the {@code /api/v1/settings/temperature} endpoints.
 *
 * @param temperature
 *            the current translation temperature
 * @param min
 *            minimum allowed temperature
 * @param max
 *            maximum allowed temperature
 * @param defaultValue
 *            the default temperature used when nothing is set
 */
public record TemperatureResponse(double temperature, double min, double max, double defaultValue) {
}
