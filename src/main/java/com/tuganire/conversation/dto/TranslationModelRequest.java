package com.tuganire.conversation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /api/v1/settings/translation-model}.
 *
 * @param model
 *            id of the model to activate; must be one of the configured selectable models
 */
public record TranslationModelRequest(@NotBlank(message = "model must not be blank") String model) {
}
