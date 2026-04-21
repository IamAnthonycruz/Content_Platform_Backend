package com.pm.content_platform_backend.auth.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class PasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final Pattern LETTER = Pattern.compile("[a-zA-Z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true; // null handled by @NotBlank
        }
        if (password.length() < 8 || password.length() > 128) {
            return false;
        }
        return LETTER.matcher(password).find()
                && DIGIT.matcher(password).find();
    }
}