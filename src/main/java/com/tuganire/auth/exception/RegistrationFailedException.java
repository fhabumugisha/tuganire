package com.tuganire.auth.exception;

import com.tuganire.shared.exception.BusinessException;

/**
 * Exception thrown when user registration fails for any reason.
 */
public class RegistrationFailedException extends BusinessException {

    private static final String MESSAGE_KEY = "auth.registration.failed";

    public RegistrationFailedException() {
        super(MESSAGE_KEY);
    }

    public RegistrationFailedException(Throwable cause) {
        super(MESSAGE_KEY, cause);
    }
}
