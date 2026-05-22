package com.tuganire.auth.validation;

import com.tuganire.auth.dto.ResetPasswordRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchesValidator implements ConstraintValidator<PasswordMatches, ResetPasswordRequest> {

    @Override
    public void initialize(PasswordMatches constraintAnnotation) {
    }

    @Override
    public boolean isValid(ResetPasswordRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        if (request.getPassword() == null || request.getConfirmPassword() == null) {
            return false;
        }

        return request.getPassword().equals(request.getConfirmPassword());
    }
}
