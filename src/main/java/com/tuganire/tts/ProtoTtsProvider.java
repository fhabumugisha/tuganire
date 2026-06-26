package com.tuganire.tts;

import com.tuganire.config.ProtoTtsConfig;
import com.tuganire.tts.ProtoTtsClient.ProtoTtsRequest;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link TtsProvider} backed by the Proto.cx Voice API — a natively-pronounced Kinyarwanda voice.
 *
 * <p>
 * Proto natively models Kinyarwanda ({@code "rw"}) and Oshiwambo ({@code "kj"}); we use it for {@code "rw"}. For
 * resilience the provider <strong>falls back to the local MMS voice</strong> whenever Proto is not configured (no
 * teamspace id / token — e.g. local dev) or the remote call fails (network, quota, timeout). Callers therefore always
 * get audio, degrading gracefully from "native" to "MMS" rather than erroring.
 *
 * @see ProtoTtsConfig
 */
@Component
@Slf4j
class ProtoTtsProvider implements TtsProvider {

    static final String PROVIDER_NAME = "proto";

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("rw", "kj");
    private static final String FORMAT_MP3 = "mp3";

    private final ProtoTtsClient client;
    private final ProtoTtsConfig config;
    private final MmsTtsProvider mmsFallback;

    ProtoTtsProvider(ProtoTtsClient client, ProtoTtsConfig config, MmsTtsProvider mmsFallback) {
        this.client = client;
        this.config = config;
        this.mmsFallback = mmsFallback;
    }

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
        if (!config.isConfigured()) {
            log.warn("Proto TTS not configured (missing teamspace id/token); using MMS fallback for lang={}",
                    languageCode);
            return mmsFallback.synthesize(text, languageCode);
        }
        try {
            log.debug("Proto TTS synthesize: lang={}, gender={}, text length={}", languageCode, config.gender(),
                    text.length());
            return client.synthesize(config.subcompanyId(),
                    new ProtoTtsRequest(text, languageCode, FORMAT_MP3, config.gender()));
        } catch (RuntimeException ex) {
            log.warn("Proto TTS call failed for lang={} ({}); using MMS fallback", languageCode, ex.getMessage());
            return mmsFallback.synthesize(text, languageCode);
        }
    }
}
