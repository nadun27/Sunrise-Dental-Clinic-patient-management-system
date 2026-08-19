package com.sunrisedental.util;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtil {

    private static final int WORK_FACTOR = 12;

    private PasswordUtil() {
    }

    public static String hash(String password) {
        if (password == null || password.length() < 10) {
            throw new IllegalArgumentException(
                    "Password must contain at least 10 characters"
            );
        }

        return BCrypt.hashpw(
                password,
                BCrypt.gensalt(WORK_FACTOR)
        );
    }

    public static boolean matches(
            String password,
            String storedHash
    ) {
        if (password == null || storedHash == null) {
            return false;
        }

        try {
            return BCrypt.checkpw(password, storedHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}