package com.tuganire.stt;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Default implementation of {@link SttService}.
 *
 * <p>
 * Resolves the appropriate {@link SttProvider} for the requested language via {@link SttProviderFactory}, delegates
 * transcription, and increments the {@code tuganire.providers.usage} Micrometer counter so admin dashboards stay
 * accurate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class SttServiceImpl implements SttService {

    private static final String COUNTER_NAME = "tuganire.providers.usage";
    private static final String TAG_TYPE = "type";
    private static final String TAG_NAME = "name";
    private static final String TYPE_STT = "stt";

    private final SttProviderFactory providerFactory;
    private final MeterRegistry meterRegistry;

    @Override
    public String transcribe(byte[] audio, Locale language) {
        String langCode = language.getLanguage();
        SttProvider provider = providerFactory.forLanguage(langCode);

        log.debug("SttServiceImpl: transcribing {} bytes in '{}' via provider '{}'", audio.length, langCode,
                provider.name());

        String transcript = provider.transcribe(audio, language);

        meterRegistry.counter(COUNTER_NAME, TAG_TYPE, TYPE_STT, TAG_NAME, provider.name()).increment();

        return transcript;
    }
}
