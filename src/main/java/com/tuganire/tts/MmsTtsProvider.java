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

    /**
     * Always request punctuation-aware pauses. MMS is the Kinyarwanda voice and runs words together with no
     * articulation; inserting silences at punctuation makes it clearer and easier to follow.
     */
    private static final boolean USE_PAUSES = true;

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
        return mmsTtsClient.synthesize(new MmsTtsRequest(text, languageCode, USE_PAUSES));
    }
}
