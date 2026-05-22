package com.tuganire.payment.exception;

import com.tuganire.shared.exception.BusinessException;
import lombok.Getter;

@Getter
public class StripeException extends BusinessException {

    private final String errorCode;
    private final String errorType;

    public StripeException(String messageKey, String errorCode, String errorType) {
        super(messageKey);
        this.errorCode = errorCode;
        this.errorType = errorType;
    }

    public StripeException(String messageKey, Throwable cause) {
        super(messageKey, cause);
        this.errorCode = null;
        this.errorType = null;
    }
}
