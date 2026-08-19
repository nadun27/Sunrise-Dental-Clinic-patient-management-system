package com.sunrisedental.dao;

import com.sunrisedental.model.User;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

public interface UserDao {

    Optional<User> findByUsername(String username)
            throws SQLException;

    void recordSuccessfulLogin(long userId)
            throws SQLException;

    void recordFailedLogin(
            long userId,
            int failedAttempts,
            LocalDateTime lockedUntil
    ) throws SQLException;
}