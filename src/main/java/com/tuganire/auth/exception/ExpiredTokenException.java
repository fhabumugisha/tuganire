package com.tuganire.auth.exception;

import com.tuganire.shared.exception.BusinessException;

public class ExpiredTokenException extends BusinessException {

    public ExpiredTokenException(String message) {
        super(message);
    }
}
