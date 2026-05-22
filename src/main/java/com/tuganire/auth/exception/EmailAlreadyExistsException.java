package com.tuganire.auth.exception;

import com.tuganire.shared.exception.BusinessException;

/**
 * Exception thrown when attempting to register with an email that already exists.
 */
public class EmailAlreadyExistsException extends BusinessException {

    private static final String MESSAGE_KEY = "auth.email.already.exists";

    public EmailAlreadyExistsException() {
        super(MESSAGE_KEY);
    }

    public EmailAlreadyExistsException(String email) {
        super(MESSAGE_KEY);
    }
}
