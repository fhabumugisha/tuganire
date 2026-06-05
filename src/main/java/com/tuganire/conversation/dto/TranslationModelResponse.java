package com.tuganire.conversation.dto;

import java.util.List;

/**
 * Response for the {@code /api/v1/settings/translation-model} endpoints.
 *
 * @param model
 *            the active translation model id
 * @param available
 *            the selectable models (id, label, provider)
 */
public record TranslationModelResponse(String model, List<ModelOptionDto> available) {
}
