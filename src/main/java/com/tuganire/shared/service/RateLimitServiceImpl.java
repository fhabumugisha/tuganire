package com.tuganire.shared.service;

import com.tuganire.shared.exception.TooManyRequestException;
import com.tuganire.shared.util.ClientIpUtils;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@Slf4j
public class RateLimitServiceImpl implements RateLimitService, DisposableBean {

    /** Maximum requests allowed per period */
    public static final int MAX_REQUESTS = 5;

    /** Period duration in minutes */
    private static final long DURATION_MINUTES = 60;

    /** Cleanup interval for expired buckets (in minutes) */
    private static final long CLEANUP_INTERVAL_MINUTES = 5;

    /** Time format for display */
    private static final String TIME_FORMAT = "HH:mm";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern(TIME_FORMAT);

    private final Map<String, BucketInfo> buckets = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredBuckets, CLEANUP_INTERVAL_MINUTES,
                CLEANUP_INTERVAL_MINUTES, TimeUnit.MINUTES);
        log.info("Rate limit service initialized with {} requests per {} minutes", MAX_REQUESTS, DURATION_MINUTES);
    }

    @Override
    public void destroy() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(1, TimeUnit.MINUTES)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupExecutor.shutdownNow();
        }
    }

    @Override
    public boolean canMakeRequest(String clientIP) {
        // Use computeIfAbsent for atomic get-or-create without global lock
        BucketInfo bucketInfo = getOrCreateBucketInfo(clientIP);
        return bucketInfo.bucket().getAvailableTokens() > 0;
    }

    @Override
    public void consumeToken(String clientIP) {
        // Use computeIfAbsent for atomic get-or-create without global lock
        BucketInfo bucketInfo = getOrCreateBucketInfo(clientIP);
        long remainingTokens = bucketInfo.bucket().getAvailableTokens();
        // Bucket4j's tryConsume is thread-safe
        boolean consumed = bucketInfo.bucket().tryConsume(1);

        if (!consumed) {
            log.warn("Rate limit exceeded for IP: {}. Reset at: {}", clientIP, formatResetTime(bucketInfo.resetTime()));
            throw new TooManyRequestException("Rate limit exceeded. Please try again later.", "rate.limit.exceeded",
                    new Object[]{formatResetTime(bucketInfo.resetTime())}, bucketInfo.resetTime(), MAX_REQUESTS, 0);
        }

        log.debug("Token consumed for IP: {}. Remaining tokens: {}/{}, reset at: {}", clientIP, remainingTokens - 1,
                MAX_REQUESTS, formatResetTime(bucketInfo.resetTime()));
    }

    @Override
    public long getRemainingRequests() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr == null) {
            return MAX_REQUESTS;
        }

        HttpServletRequest request = attr.getRequest();
        String clientIP = ClientIpUtils.getClientIP(request);

        // ConcurrentHashMap.get() is thread-safe, no lock needed
        BucketInfo bucketInfo = buckets.get(clientIP);
        if (bucketInfo == null || System.currentTimeMillis() >= bucketInfo.resetTime()) {
            return MAX_REQUESTS;
        }
        return bucketInfo.bucket().getAvailableTokens();
    }

    @Override
    public long getMaxRequests() {
        return MAX_REQUESTS;
    }

    @Override
    public long getResetTime() {
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attr == null) {
                return Instant.now().plus(Duration.ofMinutes(DURATION_MINUTES)).toEpochMilli();
            }

            HttpServletRequest request = attr.getRequest();
            String clientIP = ClientIpUtils.getClientIP(request);

            // ConcurrentHashMap.get() is thread-safe, no lock needed
            BucketInfo bucketInfo = buckets.get(clientIP);
            if (bucketInfo == null) {
                return Instant.now().plus(Duration.ofMinutes(DURATION_MINUTES)).toEpochMilli();
            }
            return bucketInfo.resetTime();
        } catch (Exception e) {
            log.error("Error getting reset time", e);
            return Instant.now().plus(Duration.ofMinutes(DURATION_MINUTES)).toEpochMilli();
        }
    }

    @Override
    public String getNextResetTimeFormatted() {
        try {
            long resetTime = getResetTime();
            long now = System.currentTimeMillis();
            if (resetTime <= now) {
                Instant nextReset = Instant.now().plus(Duration.ofMinutes(DURATION_MINUTES));
                LocalDateTime dateTime = LocalDateTime.ofInstant(nextReset, ZoneId.systemDefault());
                dateTime = dateTime.truncatedTo(ChronoUnit.MINUTES);
                resetTime = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
            return formatResetTime(resetTime);
        } catch (Exception e) {
            log.error("Error formatting reset time", e);
            Instant futureTime = Instant.now().plus(Duration.ofMinutes(DURATION_MINUTES));
            return formatResetTime(futureTime.toEpochMilli());
        }
    }

    @Override
    public void addRateLimitHeaders(HttpServletResponse response, String clientIP) {
        // Use computeIfAbsent for atomic get-or-create without global lock
        BucketInfo bucketInfo = getOrCreateBucketInfo(clientIP);
        long remainingTokens = bucketInfo.bucket().getAvailableTokens();

        response.setHeader("X-Rate-Limit-Limit", String.valueOf(MAX_REQUESTS));
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(remainingTokens));
        response.setHeader("X-Rate-Limit-Reset", String.valueOf(bucketInfo.resetTime()));
        // Note: Retry-After should only be set on 429 responses, not on every request
        // It's set in GlobalExceptionHandler when rate limit is exceeded
    }

    private BucketInfo getOrCreateBucketInfo(String clientIP) {
        long now = System.currentTimeMillis();

        // Use compute() for atomic check-and-create/replace operation
        // This avoids the global synchronized block while ensuring thread safety
        return buckets.compute(clientIP, (key, existingBucket) -> {
            if (existingBucket == null || now >= existingBucket.resetTime()) {
                log.info("Creating new bucket for IP: {}", clientIP);
                return createNewBucketInfo(clientIP);
            }
            return existingBucket;
        });
    }

    private BucketInfo createNewBucketInfo(String key) {
        Instant now = Instant.now();
        LocalDateTime dateTime = LocalDateTime.ofInstant(now, ZoneId.systemDefault());
        dateTime = dateTime.truncatedTo(ChronoUnit.MINUTES).plusMinutes(DURATION_MINUTES);
        long resetTimeMillis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        Bucket bucket = Bucket.builder().addLimit(Bandwidth.builder().capacity(MAX_REQUESTS)
                .refillGreedy(MAX_REQUESTS, Duration.ofMinutes(DURATION_MINUTES)).build()).build();

        return new BucketInfo(bucket, resetTimeMillis);
    }

    private void cleanupExpiredBuckets() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> {
            if (entry.getValue().resetTime() < now) {
                log.debug("Removing expired bucket for IP: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    // Removed duplicate getClientIP() - use ClientIpUtils.getClientIP() instead

    private String formatResetTime(long resetTime) {
        Instant instant = Instant.ofEpochMilli(resetTime);
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return dateTime.format(TIME_FORMATTER);
    }

    private record BucketInfo(Bucket bucket, long resetTime) {
    }
}
