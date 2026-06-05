package com.tuganire.stt;

import java.util.Locale;

/**
 * Service for converting spoken audio to text.
 *
 * <p>
 * Delegates to the best-available {@link SttProvider} for the requested language (via {@link SttProviderFactory}).
 * Kinyarwanda ({@code "rw"}) will throw a {@link com.tuganire.shared.exception.BusinessException} until a
 * Kinyarwanda-capable provider is registered (see {@code STT-KINYARWANDA-NOTE.md}).
 */
public interface SttService {

    /**
     * Transcribes audio bytes to text in the given language.
     *
     * @param audio
     *            raw audio bytes (WAV, MP3, M4A, or WebM)
     * @param language
     *            expected spoken language
     * @return transcribed text
     * @throws com.tuganire.shared.exception.BusinessException
     *             if no provider supports the language (e.g. Kinyarwanda in Sprint 1)
     */
    String transcribe(byte[] audio, Locale language);
}
