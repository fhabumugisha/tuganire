package com.tuganire.stt;

import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link SttProvider} backed by the local Python MMS-ASR server.
 *
 * <p>
 * Supports Kinyarwanda ({@code "rw"}), which OpenAI Whisper does not cover. Meta MMS ({@code mms-1b-all}) recognises
 * 1000+ languages including Kinyarwanda, so {@link SttProviderFactory#forLanguage} routes {@code "rw"} here instead of
 * throwing the previous "no rw provider" error.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class MmsSttProvider implements SttProvider {

    static final String PROVIDER_NAME = "mms";

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("rw");

    private final MmsSttClient mmsSttClient;

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supportsLanguage(String languageCode) {
        return SUPPORTED_LANGUAGES.contains(languageCode);
    }

    @Override
    public String transcribe(byte[] audio, Locale language) {
        String langCode = language.getLanguage();
        log.debug("MMS STT transcribe: lang={}, {} bytes", langCode, audio.length);
        MmsSttClient.MmsSttResponse response = mmsSttClient.transcribe(audio, langCode);
        return response != null && response.text() != null ? response.text() : "";
    }
}
