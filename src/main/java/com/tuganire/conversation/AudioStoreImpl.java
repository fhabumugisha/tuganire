package com.tuganire.conversation;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed implementation of {@link AudioStore}. Audio bytes are stored under the key {@code tuganire:audio:{id}}
 * with a 5-minute TTL. The byte-array value is serialised with the default (JDK-serialisation) serialiser configured on
 * the injected template.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class AudioStoreImpl implements AudioStore {

    static final String KEY_PREFIX = "tuganire:audio:";
    static final Duration AUDIO_TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, byte[]> audioRedisTemplate;

    @Override
    public void store(String id, byte[] audioBytes) {
        String key = KEY_PREFIX + id;
        audioRedisTemplate.opsForValue().set(key, audioBytes, AUDIO_TTL);
        log.debug("Audio stored: key={} bytes={}", key, audioBytes.length);
    }

    @Override
    public Optional<byte[]> getAudio(String id) {
        String key = KEY_PREFIX + id;
        @Nullable
        byte[] bytes = audioRedisTemplate.opsForValue().get(key);
        if (bytes == null) {
            log.debug("Audio cache miss: key={}", key);
            return Optional.empty();
        }
        log.debug("Audio cache hit: key={} bytes={}", key, bytes.length);
        return Optional.of(bytes);
    }
}
