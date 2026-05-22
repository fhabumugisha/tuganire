package com.tuganire.shared.util;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

/**
 * Utility class for extracting client IP address from HTTP requests. Handles proxy headers (X-Forwarded-For) and
 * comma-separated IP lists.
 *
 * SECURITY NOTE: X-Forwarded-For can be spoofed by clients. In production, ensure your reverse proxy (nginx,
 * CloudFlare, etc.) overwrites this header. This utility should only be used behind a trusted proxy.
 */
public final class ClientIpUtils {

    private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String X_REAL_IP_HEADER = "X-Real-IP";
    private static final String UNKNOWN = "unknown";
    private static final String FALLBACK_IP = "0.0.0.0";

    /**
     * Known private/local IP ranges that indicate the request came through a proxy. When remoteAddr is one of these, we
     * should trust proxy headers.
     */
    private static final Set<String> TRUSTED_PROXY_INDICATORS = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private ClientIpUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Extracts the client IP address from the HTTP request. Handles X-Forwarded-For and X-Real-IP headers for clients
     * behind proxies.
     *
     * Security: Only trusts proxy headers when request comes from localhost/known proxy. Direct internet requests use
     * remoteAddr to prevent IP spoofing.
     *
     * @param request
     *            the HTTP servlet request
     * @return the client IP address, never null (returns "0.0.0.0" as fallback)
     */
    public static String getClientIP(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String clientIP = null;

        // Only trust proxy headers if request comes from a trusted proxy
        if (remoteAddr != null && (TRUSTED_PROXY_INDICATORS.contains(remoteAddr) || remoteAddr.startsWith("10.")
                || remoteAddr.startsWith("172.") || remoteAddr.startsWith("192.168."))) {

            // Try X-Forwarded-For first
            clientIP = request.getHeader(X_FORWARDED_FOR_HEADER);

            // Fallback to X-Real-IP (set by nginx)
            if (clientIP == null || clientIP.isEmpty() || UNKNOWN.equalsIgnoreCase(clientIP)) {
                clientIP = request.getHeader(X_REAL_IP_HEADER);
            }
        }

        // Use remoteAddr if no valid proxy header
        if (clientIP == null || clientIP.isEmpty() || UNKNOWN.equalsIgnoreCase(clientIP)) {
            clientIP = remoteAddr;
        }

        // Handle comma-separated IPs (take first one - original client)
        if (clientIP != null && clientIP.contains(",")) {
            clientIP = clientIP.split(",")[0].trim();
        }

        // Never return null to prevent NPE in ConcurrentHashMap
        return clientIP != null && !clientIP.isEmpty() ? clientIP : FALLBACK_IP;
    }
}
