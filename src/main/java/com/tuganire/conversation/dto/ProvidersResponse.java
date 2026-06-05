package com.tuganire.conversation.dto;

import java.util.List;

/**
 * Response for {@code GET /api/v1/providers}.
 *
 * @param ttsProviders
 *            names of registered TTS providers
 * @param sttProviders
 *            names of registered STT providers
 * @param llmProviders
 *            names of registered LLM providers
 * @param activeTtsProvider
 *            name of the currently active TTS provider
 */
public record ProvidersResponse(List<String> ttsProviders, List<String> sttProviders, List<String> llmProviders,
        String activeTtsProvider) {
}
