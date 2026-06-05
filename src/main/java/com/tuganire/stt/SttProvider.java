package com.tuganire.stt;

import java.util.Locale;

/**
 * Abstraction over any speech-to-text backend.
 *
 * <p>
 * Implementations are swappable at runtime (ADR-004). No {@code org.springframework.ai.*} types appear here.
 */
public interface SttProvider {

    /**
     * Transcribes audio bytes to text in the given language.
     *
     * @param audio
     *            raw audio bytes (WAV or WebM, provider-dependent)
     * @param language
     *            the expected spoken language
     * @return transcribed text
     */
    String transcribe(byte[] audio, Locale language);

    /**
     * Returns the canonical name of this provider (e.g. {@code "whisper"}, {@code "google"}).
     *
     * @return provider name
     */
    String name();

    /**
     * Returns {@code true} if this provider can transcribe audio in the given language code.
     *
     * @param languageCode
     *            BCP-47 language code
     * @return {@code true} when supported
     */
    boolean supportsLanguage(String languageCode);
}
