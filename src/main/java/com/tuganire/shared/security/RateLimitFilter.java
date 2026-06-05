package com.tuganire.shared.security;

import com.tuganire.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that enforces per-session rate limits backed by Redis.
 *
 * <p>
 * The session identity is taken from the {@value #SESSION_ID_HEADER} request header; if absent, the client IP address
 * is used as a fallback. Two independent windows are checked atomically via a Lua script:
 *
 * <ul>
 * <li>Per-minute (TTL 60 s): configured by {@code tuganire.rate-limit.requests-per-minute}
 * <li>Per-day (TTL 86 400 s): configured by {@code tuganire.rate-limit.requests-per-day}
 * </ul>
 *
 * <p>
 * When a limit is exceeded the filter short-circuits the chain and returns HTTP 429 with a JSON error body. No PII or
 * request content is logged.
 *
 * <p>
 * Virtual-thread safe: no {@code synchronized} blocks; Redis operations are atomic via Lua.
 */
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    /** Header name clients use to advertise their anonymous session ID. */
    public static final String SESSION_ID_HEADER = "X-Session-Id";

    /** Redis key prefix for per-minute counters. */
    private static final String KEY_PREFIX_MINUTE = "rl:min:";

    /** Redis key prefix for per-day counters. */
    private static final String KEY_PREFIX_DAY = "rl:day:";

    private static final int TTL_MINUTE_SECONDS = 60;
    private static final int TTL_DAY_SECONDS = (int) TimeUnit.DAYS.toSeconds(1);

    /**
     * Lua script: atomically increment a counter key and set TTL on first access. Returns the new counter value.
     * Arguments: KEYS[1] = key, ARGV[1] = TTL in seconds.
     */
    private static final DefaultRedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('INCR', KEYS[1])\n" + "if v == 1 then\n"
                    + "  redis.call('EXPIRE', KEYS[1], ARGV[1])\n" + "end\n" + "return v",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties rateLimitProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String sessionId = resolveSessionId(request);

        if (isLimitExceeded(sessionId)) {
            log.debug("Rate limit exceeded for session bucket '{}'", sessionId);
            sendTooManyRequests(response);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Resolves the bucket key from the incoming request. Prefers {@value #SESSION_ID_HEADER}; falls back to the remote
     * address.
     */
    private String resolveSessionId(HttpServletRequest request) {
        String header = request.getHeader(SESSION_ID_HEADER);
        if (header != null && !header.isBlank()) {
            // Sanitise: keep only safe characters to avoid Redis key injection
            return header.replaceAll("[^a-zA-Z0-9\\-_]", "").substring(0, Math.min(header.length(), 64));
        }
        return request.getRemoteAddr();
    }

    /**
     * Returns {@code true} when either the per-minute or per-day ceiling has been reached. Both counters are
     * incremented in one trip each; the per-minute check is performed first.
     */
    private boolean isLimitExceeded(String sessionId) {
        try {
            long minuteCount = increment(KEY_PREFIX_MINUTE + sessionId, TTL_MINUTE_SECONDS);
            if (minuteCount > rateLimitProperties.requestsPerMinute()) {
                return true;
            }
            long dayCount = increment(KEY_PREFIX_DAY + sessionId, TTL_DAY_SECONDS);
            return dayCount > rateLimitProperties.requestsPerDay();
        } catch (Exception ex) {
            // Fail-open: if Redis is unavailable, allow the request through rather than disrupting service.
            log.warn("Rate-limit Redis check failed, allowing request through: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Atomically increments {@code key} and, on first access, sets its TTL to {@code ttlSeconds}. Returns the new
     * counter value.
     */
    private long increment(String key, int ttlSeconds) {
        Long value = redisTemplate.execute(INCR_SCRIPT, List.of(key), String.valueOf(ttlSeconds));
        return value != null ? value : 1L;
    }

    /** Writes an HTTP 429 response with a structured JSON error body. */
    private void sendTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. "
                + "Please slow down and retry after a moment.\"}");
    }

    /** Only apply the rate-limit filter to API and WebSocket upgrade paths. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") && !path.startsWith("/ws/");
    }
}
