package com.sunrisedental.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilTest {

    @Test
    void hashesAndVerifiesCorrectPassword() {
        String password = "StrongPassword123!";
        String hash = PasswordUtil.hash(password);

        assertNotEquals(password, hash);
        assertTrue(
                PasswordUtil.matches(password, hash)
        );
    }

    @Test
    void rejectsIncorrectPassword() {
        String hash =
                PasswordUtil.hash("StrongPassword123!");

        assertFalse(
                PasswordUtil.matches(
                        "WrongPassword123!",
                        hash
                )
        );
    }

    @Test
    void rejectsShortPassword() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PasswordUtil.hash("short")
        );
    }
}