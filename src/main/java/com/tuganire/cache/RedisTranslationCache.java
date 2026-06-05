package com.tuganire.cache;

import com.tuganire.translation.TranslationResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed implementation of {@link TranslationCache}. Active when Redis is enabled (the default). Builds
 * deterministic keys from a SHA-256 hash of {@code (sourceText, sourceLang, targetLang)} so that FR→RW and RW→FR of the
 * same text produce distinct entries. Entries expire after {@code tuganire.cache.translation-ttl-days} days. The cached
 * copy always has {@code fromCache=true}; the stored value may have {@code fromCache=false} — it is re-wrapped on read
 * so callers and metrics see the flag correctly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.data.redis.url", matchIfMissing = true)
public class RedisTranslationCache implements TranslationCache {

    private static final String KEY_PREFIX = "tuganire:tr:";

    private final RedisTemplate<String, TranslationResponse> translationRedisTemplate;
    private final CacheProperties cacheProperties;

    @Override
    public Optional<TranslationResponse> find(String sourceText, Locale src, Locale tgt) {
        String key = buildKey(sourceText, src, tgt);
        @Nullable
        TranslationResponse cached = translationRedisTemplate.opsForValue().get(key);
        if (cached == null) {
            return Optional.empty();
        }
        // Ensure fromCache=true regardless of how the entry was stored.
        TranslationResponse result = cached.fromCache()
                ? cached
                : TranslationResponse.fromCache(cached.originalText(), cached.translatedText(),
                        cached.detectedLanguage(), cached.confidence(), cached.appliedCorrections());
        log.debug("Cache hit for key={}", key);
        return Optional.of(result);
    }

    @Override
    public void put(String sourceText, Locale src, Locale tgt, TranslationResponse response) {
        String key = buildKey(sourceText, src, tgt);
        Duration ttl = Duration.ofDays(cacheProperties.translationTtlDays());
        translationRedisTemplate.opsForValue().set(key, response, ttl);
        log.debug("Cached translation key={} ttl={}d", key, cacheProperties.translationTtlDays());
    }

    /**
     * Builds a deterministic Redis key: {@code tuganire:tr:{src}:{tgt}:{sha256(sourceText)}}. The language tags are
     * included before the hash so FR→RW and RW→FR of the same text never collide.
     */
    private String buildKey(String sourceText, Locale src, Locale tgt) {
        String hash = sha256Hex(sourceText);
        return KEY_PREFIX + src.toLanguageTag() + ":" + tgt.toLanguageTag() + ":" + hash;
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed by the JVM spec; this branch is unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
