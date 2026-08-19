package com.sunrisedental.dao.impl;

import com.sunrisedental.config.DatabaseConfig;
import com.sunrisedental.dao.UserDao;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

public class JdbcUserDao implements UserDao {

    @Override
    public Optional<User> findByUsername(String username)
            throws SQLException {

        String sql = """
                SELECT
                    u.user_id,
                    u.username,
                    u.password_hash,
                    u.full_name,
                    u.email,
                    r.role_name,
                    u.active,
                    u.failed_login_attempts,
                    u.locked_until
                FROM users u
                INNER JOIN roles r
                    ON u.role_id = r.role_id
                WHERE LOWER(u.username) = LOWER(?)
                LIMIT 1
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, username);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                Timestamp lockedTimestamp =
                        resultSet.getTimestamp("locked_until");

                LocalDateTime lockedUntil =
                        lockedTimestamp == null
                                ? null
                                : lockedTimestamp.toLocalDateTime();

                User user = new User(
                        resultSet.getLong("user_id"),
                        resultSet.getString("username"),
                        resultSet.getString("password_hash"),
                        resultSet.getString("full_name"),
                        resultSet.getString("email"),
                        Role.valueOf(
                                resultSet.getString("role_name")
                        ),
                        resultSet.getBoolean("active"),
                        resultSet.getInt(
                                "failed_login_attempts"
                        ),
                        lockedUntil
                );

                return Optional.of(user);
            }
        }
    }

    @Override
    public void recordSuccessfulLogin(long userId)
            throws SQLException {

        String sql = """
                UPDATE users
                SET failed_login_attempts = 0,
                    locked_until = NULL,
                    last_login_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    @Override
    public void recordFailedLogin(
            long userId,
            int failedAttempts,
            LocalDateTime lockedUntil
    ) throws SQLException {

        String sql = """
                UPDATE users
                SET failed_login_attempts = ?,
                    locked_until = ?
                WHERE user_id = ?
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setInt(1, failedAttempts);

            if (lockedUntil == null) {
                statement.setNull(
                        2,
                        java.sql.Types.TIMESTAMP
                );
            } else {
                statement.setTimestamp(
                        2,
                        Timestamp.valueOf(lockedUntil)
                );
            }

            statement.setLong(3, userId);
            statement.executeUpdate();
        }
    }
}