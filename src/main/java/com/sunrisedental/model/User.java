package com.sunrisedental.model;


import java.time.LocalDateTime;

public record User(
        long userId,
        String username,
        String passwordHash,
        String fullName,
        String email,
        Role role,
        boolean active,
        int failedLoginAttempts,
        LocalDateTime lockedUntil
) {
}