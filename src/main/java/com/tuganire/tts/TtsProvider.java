package com.tuganire.tts;

import java.util.Locale;

/**
 * Abstraction over any text-to-speech backend.
 *
 * <p>
 * Implementations are swappable at runtime (ARCHI section 4, ADR-004). No {@code org.springframework.ai.*} types appear
 * here.
 *
 * <p>
 * The {@link #warmup} default is a no-op; implementations that benefit from pre-warming a connection or loading a model
 * should override it. The virtual-thread pipeline in Task 17 calls warmup on startup.
 */
public interface TtsProvider {

    /**
     * Synthesises speech for the given text in the specified language.
     *
     * @param text
     *            the text to speak
     * @param languageCode
     *            BCP-47 language code (e.g. {@code "fr"}, {@code "rw"})
     * @return raw audio bytes (WAV or MP3 depending on the provider)
     */
    byte[] synthesize(String text, String languageCode);

    /**
     * Returns the canonical name of this provider (e.g. {@code "proto"}, {@code "mms"}).
     *
     * @return provider name
     */
    String name();

    /**
     * Returns {@code true} if this provider can synthesise speech for the given language code.
     *
     * @param languageCode
     *            BCP-47 language code
     * @return {@code true} when supported
     */
    boolean supportsLanguage(String languageCode);

    /**
     * Optional warm-up hook called once at application startup for the given locale.
     *
     * <p>
     * Providers that establish persistent connections or pre-load models should override this to reduce first-request
     * latency.
     *
     * @param lang
     *            the locale to warm up
     */
    default void warmup(Locale lang) {
        // no-op by default
    }
}
