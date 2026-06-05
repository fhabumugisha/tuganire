package com.tuganire.tts;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Default {@link TtsService} implementation.
 *
 * <p>
 * Delegates to the {@link TtsProviderFactory} for provider resolution, then increments the Micrometer counter
 * {@code tuganire.providers.usage} tagged with {@code type=tts} and the provider name.
 */
@Service
@RequiredArgsConstructor
@Slf4j
class TtsServiceImpl implements TtsService {

    private static final String COUNTER_NAME = "tuganire.providers.usage";
    private static final String TAG_TYPE = "type";
    private static final String TAG_TYPE_VALUE = "tts";
    private static final String TAG_NAME = "name";

    private final TtsProviderFactory factory;
    private final MeterRegistry meterRegistry;

    @Override
    public byte[] synthesize(String text, Locale language) {
        return synthesize(text, language, null);
    }

    @Override
    public byte[] synthesize(String text, Locale language, @Nullable String providerOverride) {
        String langCode = language.getLanguage();
        TtsProvider provider = providerOverride != null ? factory.get(providerOverride) : factory.forLanguage(langCode);

        log.debug("TTS synthesize: provider={}, lang={}, text length={}", provider.name(), langCode, text.length());

        byte[] audio = provider.synthesize(text, langCode);

        meterRegistry.counter(COUNTER_NAME, TAG_TYPE, TAG_TYPE_VALUE, TAG_NAME, provider.name()).increment();

        return audio;
    }
}
