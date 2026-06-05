package com.tuganire.tts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link TtsProvider} stub for the ElevenLabs TTS API (Phase 2 premium feature, PRD).
 *
 * <p>
 * Full implementation is deferred until Phase 2. When {@code ELEVENLABS_API_KEY} is set, this provider will delegate to
 * the ElevenLabs v1 speech endpoint. Until then, {@link #synthesize} throws {@link UnsupportedOperationException}.
 */
@Component
@Slf4j
class ElevenLabsTtsProvider implements TtsProvider {

    static final String PROVIDER_NAME = "elevenlabs";

    private final String apiKey;

    ElevenLabsTtsProvider(@Value("${elevenlabs.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsLanguage(String languageCode) {
        return true;
    }

    @Override
    public byte[] synthesize(String text, String languageCode) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UnsupportedOperationException(
                    "ElevenLabs TTS is not yet configured. Set the ELEVENLABS_API_KEY environment variable (Phase 2).");
        }
        // Phase 2: delegate to ElevenLabs REST API using the configured apiKey
        throw new UnsupportedOperationException("ElevenLabs TTS full implementation is deferred to Phase 2.");
    }
}
