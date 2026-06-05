package com.tuganire.cache;

import com.tuganire.translation.TranslationResponse;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Fallback {@link TranslationCache} active when no Redis-backed implementation is present (e.g. unit-test profiles that
 * do not start Redis). Always returns empty on {@code find} and discards {@code put} calls so the application boots and
 * the translation pipeline runs end-to-end without a cache layer.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(TranslationCache.class)
public class NoOpTranslationCache implements TranslationCache {

    @Override
    public Optional<TranslationResponse> find(String sourceText, Locale src, Locale tgt) {
        return Optional.empty();
    }

    @Override
    public void put(String sourceText, Locale src, Locale tgt, TranslationResponse response) {
        log.debug("NoOpTranslationCache: put ignored (Redis not configured)");
    }
}
