package com.tuganire.conversation.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /api/v1/providers/tts}.
 *
 * @param providerName
 *            canonical name of the TTS provider to activate
 */
public record TtsProviderSwitchRequest(@NotBlank(message = "providerName must not be blank") String providerName) {
}
