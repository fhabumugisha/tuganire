package com.tuganire.cache;

import com.tuganire.translation.TranslationResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis infrastructure beans for the translation cache. The {@link RedisTemplate} uses a {@link StringRedisSerializer}
 * for keys and a Jackson 3 JSON serializer for values so stored entries are human-readable and type-safe.
 */
@Configuration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    @Bean
    public RedisTemplate<String, TranslationResponse> translationRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, TranslationResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JacksonJsonRedisSerializer<>(TranslationResponse.class));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new JacksonJsonRedisSerializer<>(TranslationResponse.class));
        return template;
    }
}
