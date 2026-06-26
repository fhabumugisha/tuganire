package com.tuganire.tts;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Converts text to speech audio bytes.
 *
 * <p>
 * Provider selection is based on the language of the text. An optional {@code providerOverride} allows callers (e.g.
 * the settings comparator in US-09) to force a specific provider by name.
 */
public interface TtsService {

    /**
     * Synthesises speech for the given text and language, using the configured provider for that language.
     *
     * @param text
     *            the text to speak
     * @param language
     *            locale that determines which TTS provider is selected
     * @return raw audio bytes (format depends on the provider)
     */
    byte[] synthesize(String text, Locale language);

    /**
     * Synthesises speech, optionally overriding the default provider selection.
     *
     * @param text
     *            the text to speak
     * @param language
     *            locale used for provider routing when {@code providerOverride} is {@code null}
     * @param providerOverride
     *            canonical provider name to force (e.g. {@code "proto"}), or {@code null} to use the default routing
     * @return raw audio bytes (format depends on the provider)
     */
    byte[] synthesize(String text, Locale language, @Nullable String providerOverride);
}
