package com.tuganire.shared.config;

import com.tuganire.shared.service.RateLimitService;
import com.tuganire.shared.util.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitService rateLimitService;

    /**
     * Pattern to match static resource file extensions. Only skip rate limiting for actual static files, not arbitrary
     * paths containing dots.
     */
    private static final Pattern STATIC_RESOURCE_PATTERN = Pattern.compile(
            ".*\\.(css|js|png|jpg|jpeg|gif|svg|ico|woff|woff2|ttf|eot|map|webp|avif)$", Pattern.CASE_INSENSITIVE);

    /**
     * Static resource path prefixes that should bypass rate limiting.
     */
    private static final String[] STATIC_PATH_PREFIXES = {"/css/", "/js/", "/images/", "/static/", "/webjars/",
            "/fonts/"};

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // Skip OPTIONS requests (CORS preflight)
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();

        // Skip static resources by known path prefixes
        for (String prefix : STATIC_PATH_PREFIXES) {
            if (path.startsWith(prefix) || path.contains(prefix)) {
                return true;
            }
        }

        // Skip static resources by file extension (whitelist approach)
        if (STATIC_RESOURCE_PATTERN.matcher(path).matches()) {
            return true;
        }

        String clientIP = ClientIpUtils.getClientIP(request);
        log.debug("Processing request from IP: {}, URI: {}", clientIP, request.getRequestURI());

        // Add rate limit headers only - token consumption happens in controllers
        // for specific expensive operations (e.g., AI generation)
        rateLimitService.addRateLimitHeaders(response, clientIP);

        return true;
    }
}
