package com.sunrisedental.service;

import com.sunrisedental.model.User;

public record AuthResult(
        boolean success,
        String message,
        User user
) {

    public static AuthResult success(User user) {
        return new AuthResult(
                true,
                "Login successful",
                user
        );
    }

    public static AuthResult failure(String message) {
        return new AuthResult(
                false,
                message,
                null
        );
    }
}