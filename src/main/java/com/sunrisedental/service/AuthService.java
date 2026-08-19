package com.sunrisedental.service;

import com.sunrisedental.dao.UserDao;
import com.sunrisedental.model.User;
import com.sunrisedental.util.PasswordUtil;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserDao userDao;

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    public AuthResult authenticate(
            String username,
            String password
    ) throws SQLException {

        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {

            return AuthResult.failure(
                    "Username and password are required"
            );
        }

        String normalizedUsername =
                username.trim().toLowerCase(Locale.ROOT);

        Optional<User> optionalUser =
                userDao.findByUsername(normalizedUsername);

        if (optionalUser.isEmpty()) {
            return AuthResult.failure(
                    "Invalid username or password"
            );
        }

        User user = optionalUser.get();
        LocalDateTime now = LocalDateTime.now();

        if (!user.active()) {
            return AuthResult.failure(
                    "This account is unavailable"
            );
        }

        if (user.lockedUntil() != null
                && user.lockedUntil().isAfter(now)) {

            return AuthResult.failure(
                    "Account temporarily locked. Try again later"
            );
        }

        if (!PasswordUtil.matches(
                password,
                user.passwordHash()
        )) {
            int failedAttempts =
                    user.failedLoginAttempts() + 1;

            LocalDateTime lockedUntil = null;

            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                lockedUntil =
                        now.plusMinutes(LOCK_MINUTES);
            }

            userDao.recordFailedLogin(
                    user.userId(),
                    failedAttempts,
                    lockedUntil
            );

            if (lockedUntil != null) {
                return AuthResult.failure(
                        "Account temporarily locked for 15 minutes"
                );
            }

            return AuthResult.failure(
                    "Invalid username or password"
            );
        }

        userDao.recordSuccessfulLogin(user.userId());

        return AuthResult.success(user);
    }
}