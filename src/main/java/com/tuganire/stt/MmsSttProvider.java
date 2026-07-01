package com.tuganire.stt;

import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;

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

    /**
     * {@inheritDoc}
     *
     * <p>
     * Retries only on gateway 5xx (502/503/504) — the Railway edge's response while the scale-to-zero MMS server wakes.
     * A read timeout (connection accepted but no response) means the server is busy inferring and is treated as
     * terminal — retrying would pile a second heavy job onto it. {@code @ConcurrencyLimit} caps concurrent calls so a
     * wake spike cannot overwhelm the single MMS instance.
     */
    @Override
    @Retryable(maxRetries = 1, delay = 2000L, multiplier = 2.0, jitter = 500L, maxDelay = 8000L, includes = {
            HttpServerErrorException.BadGateway.class, HttpServerErrorException.ServiceUnavailable.class,
            HttpServerErrorException.GatewayTimeout.class})
    @ConcurrencyLimit(3)
    public String transcribe(byte[] audio, Locale language) {
        String langCode = language.getLanguage();
        log.debug("MMS STT transcribe: lang={}, {} bytes", langCode, audio.length);
        MmsSttClient.MmsSttResponse response = mmsSttClient.transcribe(audio, langCode);
        return response != null && response.text() != null ? response.text() : "";
    }
}
