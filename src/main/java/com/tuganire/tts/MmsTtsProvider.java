package com.tuganire.tts;

import com.tuganire.tts.MmsTtsClient.MmsTtsRequest;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link TtsProvider} backed by the local Python MMS-TTS server (ADR-006).
 *
 * <p>
 * Supports Kinyarwanda ({@code "rw"}) and French ({@code "fr"}) — the two languages MMS natively models.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class MmsTtsProvider implements TtsProvider {

    static final String PROVIDER_NAME = "mms";

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("rw", "fr");

    private final MmsTtsClient mmsTtsClient;

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsLanguage(String languageCode) {
        return SUPPORTED_LANGUAGES.contains(languageCode);
    }

    @Override
    public byte[] synthesize(String text, String languageCode) {
        log.debug("MMS TTS synthesize: lang={}, text length={}", languageCode, text.length());
        return mmsTtsClient.synthesize(new MmsTtsRequest(text, languageCode));
    }
}
