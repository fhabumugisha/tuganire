package com.tuganire.translation;

import java.util.Locale;

/**
 * Single entry point for text translation in the Tuganire pipeline.
 *
 * <p>
 * Orchestrates the full cache→golden-dictionary→LLM→post-processor flow (ARCHI section 5). Every caller — REST
 * controllers (Task 18), conversation pipeline (Task 17), and WebSocket handler (Task 19) — goes through this
 * interface.
 *
 * <p>
 * Guarantees:
 * <ul>
 * <li>A cached result is returned immediately (no downstream I/O).
 * <li>A golden-dictionary hit is returned after a cache write (no LLM call).
 * <li>LLM output passes through {@code KinyarwandaPostProcessor} before being cached and returned.
 * <li>French source text is normalised before lookup; Kinyarwanda source is passed as-is.
 * <li>When the default LLM provider fails, the service falls back to the configured fallback provider.
 * </ul>
 */
public interface TranslationService {

    /**
     * Translates {@code sourceText} from locale {@code src} to locale {@code tgt}.
     *
     * @param sourceText
     *            the text to translate; non-null, non-blank
     * @param src
     *            the source language locale
     * @param tgt
     *            the target language locale
     * @return the translation response, never {@code null}
     */
    TranslationResponse translate(String sourceText, Locale src, Locale tgt);

    /**
     * Convenience overload that delegates to {@link #translate(String, Locale, Locale)}.
     *
     * @param request
     *            the full translation request; non-null
     * @return the translation response, never {@code null}
     */
    TranslationResponse translate(TranslationRequest request);
}
