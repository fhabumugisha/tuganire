package com.tuganire.shared.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Service for rate limiting of requests. Controls the number of requests allowed per client in a given period.
 */
public interface RateLimitService {

    /**
     * Checks if the client can make a request.
     *
     * @param clientIP
     *            the client's IP address
     * @return true if the client can make a request, false otherwise
     */
    boolean canMakeRequest(String clientIP);

    /**
     * Consumes a rate limit token for the given client.
     *
     * @param clientIP
     *            the client's IP address
     * @throws TooManyRequestException
     *             if the limit is exceeded
     */
    void consumeToken(String clientIP);

    /**
     * Gets the number of remaining requests for the current client.
     *
     * @return the number of remaining requests
     */
    long getRemainingRequests();

    /**
     * Gets the maximum number of requests allowed.
     *
     * @return the maximum number of requests
     */
    long getMaxRequests();

    /**
     * Gets the timestamp when limits will be reset.
     *
     * @return the reset timestamp in milliseconds
     */
    long getResetTime();

    /**
     * Gets the formatted reset time.
     *
     * @return the reset time formatted as HH:mm
     */
    String getNextResetTimeFormatted();

    /**
     * Adds rate limit headers to the HTTP response.
     *
     * @param response
     *            the HTTP response
     * @param clientIP
     *            the client's IP address
     */
    void addRateLimitHeaders(HttpServletResponse response, String clientIP);
}
