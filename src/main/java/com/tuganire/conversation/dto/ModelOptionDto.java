package com.tuganire.conversation.dto;

/**
 * A selectable model exposed to the settings UI.
 *
 * @param id
 *            model id
 * @param label
 *            human-friendly label
 * @param provider
 *            owning provider name
 */
public record ModelOptionDto(String id, String label, String provider) {
}
