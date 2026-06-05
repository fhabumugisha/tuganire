package com.tuganire.cache;

import com.tuganire.translation.TranslationResponse;
import java.util.Locale;
import java.util.Optional;

/**
 * Cache for translation results. Checked before the normalizer/golden-dictionary/LLM pipeline and written back after a
 * successful translation. Audio responses are NOT cached here.
 */
public interface TranslationCache {

    /**
     * Look up a previously cached translation.
     *
     * @param sourceText
     *            text that was translated
     * @param src
     *            source language locale
     * @param tgt
     *            target language locale
     * @return the cached response with {@code fromCache=true}, or empty if not present
     */
    Optional<TranslationResponse> find(String sourceText, Locale src, Locale tgt);

    /**
     * Store a translation result. TTL is determined by the cache implementation.
     *
     * @param sourceText
     *            text that was translated
     * @param src
     *            source language locale
     * @param tgt
     *            target language locale
     * @param response
     *            the translation to cache
     */
    void put(String sourceText, Locale src, Locale tgt, TranslationResponse response);
}
