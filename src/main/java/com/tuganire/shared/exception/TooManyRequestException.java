package com.tuganire.shared.exception;

import lombok.Getter;

/**
 * Exception thrown when a rate limit is exceeded.
 */
@Getter
public class TooManyRequestException extends RuntimeException {

    private final String messageKey;
    private final Object[] args;
    private final long resetTime;
    private final int maxRequests;
    private final int remainingRequests;

    public TooManyRequestException(String message, String messageKey, Object[] args, long resetTime, int maxRequests,
            int remainingRequests) {
        super(message);
        this.messageKey = messageKey;
        this.args = args;
        this.resetTime = resetTime;
        this.maxRequests = maxRequests;
        this.remainingRequests = remainingRequests;
    }

    public TooManyRequestException(String message, String messageKey) {
        this(message, messageKey, null, 0, 0, 0);
    }
}
