package com.tuganire.tts;

import com.tuganire.config.TtsConfig;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolves a {@link TtsProvider} by canonical name or by language code.
 *
 * <p>
 * All {@link TtsProvider} beans in the application context are collected at startup. Per-language routing follows
 * {@link TtsConfig}: {@code kinyProvider} is used for {@code "rw"} and {@code frenchProvider} for {@code "fr"}
 * (ADR-006).
 */
@Service
@Slf4j
class TtsProviderFactory {

    private static final String LANG_KINY = "rw";
    private static final String LANG_FRENCH = "fr";

    private final Map<String, TtsProvider> providersByName;
    private final TtsConfig ttsConfig;

    TtsProviderFactory(List<TtsProvider> providers, TtsConfig ttsConfig) {
        this.ttsConfig = ttsConfig;
        this.providersByName = providers.stream().collect(Collectors.toMap(TtsProvider::name, Function.identity()));
        log.info("TTS providers registered: {}", providersByName.keySet());
    }

    /**
     * Returns the provider with the given canonical name.
     *
     * @param name
     *            provider name (e.g. {@code "proto"}, {@code "mms"})
     * @return the matching provider
     * @throws IllegalArgumentException
     *             if no provider with that name is registered
     */
    TtsProvider get(String name) {
        TtsProvider provider = providersByName.get(name);
        if (provider == null) {
            throw new IllegalArgumentException(
                    "Unknown TTS provider: '" + name + "'. Registered: " + providersByName.keySet());
        }
        return provider;
    }

    /**
     * Returns the configured provider for the given language code.
     *
     * <p>
     * {@code "rw"} maps to the {@code kinyProvider}, {@code "fr"} to the {@code frenchProvider}; all other codes fall
     * back to the {@code defaultProvider}.
     *
     * @param langCode
     *            BCP-47 language code
     * @return the configured provider for that language
     */
    TtsProvider forLanguage(String langCode) {
        String providerName = switch (langCode) {
            case LANG_KINY -> ttsConfig.kinyProvider();
            case LANG_FRENCH -> ttsConfig.frenchProvider();
            default -> ttsConfig.defaultProvider();
        };
        return get(providerName);
    }
}
