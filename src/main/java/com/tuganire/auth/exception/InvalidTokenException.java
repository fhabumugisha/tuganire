package com.tuganire.auth.exception;

import com.tuganire.shared.exception.BusinessException;

public class InvalidTokenException extends BusinessException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
