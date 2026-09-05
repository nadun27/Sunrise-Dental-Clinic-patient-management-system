package com.sunrisedental.dao.impl;

import com.sunrisedental.config.DatabaseConfig;
import com.sunrisedental.dao.StaffDao;
import com.sunrisedental.exception.ConflictException;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.StaffMember;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcStaffDao implements StaffDao {

    private static final String SELECT_COLUMNS = """
            SELECT
                u.user_id,
                u.username,
                u.full_name,
                u.email,
                u.contact_number,
                r.role_name,
                u.active,
                d.registration_number,
                d.specialization,
                d.consultation_fee,
                u.created_at
            FROM users u
            INNER JOIN roles r
                ON r.role_id = u.role_id
            LEFT JOIN dentists d
                ON d.user_id = u.user_id
            """;

    @Override
    public StaffMember create(
            StaffMember staffMember,
            String passwordHash
    ) throws SQLException {

        try (Connection connection =
                     DatabaseConfig.getConnection()) {

            connection.setAutoCommit(false);

            try {
                long roleId = findRoleId(
                        connection,
                        staffMember.role()
                );

                long userId = insertUser(
                        connection,
                        staffMember,
                        passwordHash,
                        roleId
                );

                if (staffMember.role() == Role.DENTIST) {
                    insertDentist(
                            connection,
                            userId,
                            staffMember
                    );
                }

                StaffMember created = findById(
                        connection,
                        userId
                ).orElseThrow(() -> new SQLException(
                        "Created staff member could not be retrieved"
                ));

                connection.commit();
                return created;

            } catch (
                    SQLIntegrityConstraintViolationException exception
            ) {
                rollback(connection, exception);

                throw new ConflictException(
                        "A staff member with the same username, " +
                                "email or registration number already exists"
                );

            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    @Override
    public List<StaffMember> search(
            String searchTerm,
            boolean includeInactive
    ) throws SQLException {

        String sql = SELECT_COLUMNS + """
                WHERE (? = TRUE OR u.active = TRUE)
                  AND (
                      ? = ''
                      OR LOWER(u.username) LIKE ?
                      OR LOWER(u.full_name) LIKE ?
                      OR LOWER(COALESCE(u.email, '')) LIKE ?
                      OR COALESCE(u.contact_number, '') LIKE ?
                      OR LOWER(r.role_name) LIKE ?
                      OR LOWER(COALESCE(d.registration_number, '')) LIKE ?
                  )
                ORDER BY u.active DESC, u.full_name ASC
                LIMIT 100
                """;

        String normalizedTerm =
                searchTerm == null
                        ? ""
                        : searchTerm.trim().toLowerCase();

        String pattern = "%" + normalizedTerm + "%";
        List<StaffMember> staffMembers = new ArrayList<>();

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setBoolean(1, includeInactive);
            statement.setString(2, normalizedTerm);

            for (int index = 3; index <= 8; index++) {
                statement.setString(index, pattern);
            }

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    staffMembers.add(mapStaffMember(resultSet));
                }
            }
        }

        return staffMembers;
    }

    @Override
    public boolean usernameExists(String username)
            throws SQLException {

        return valueExists(
                """
                SELECT 1
                FROM users
                WHERE LOWER(username) = LOWER(?)
                LIMIT 1
                """,
                username
        );
    }

    @Override
    public boolean emailExists(String email)
            throws SQLException {

        return valueExists(
                """
                SELECT 1
                FROM users
                WHERE LOWER(email) = LOWER(?)
                LIMIT 1
                """,
                email
        );
    }

    @Override
    public boolean registrationNumberExists(
            String registrationNumber
    ) throws SQLException {

        return valueExists(
                """
                SELECT 1
                FROM dentists
                WHERE LOWER(registration_number) = LOWER(?)
                LIMIT 1
                """,
                registrationNumber
        );
    }

    private long findRoleId(
            Connection connection,
            Role role
    ) throws SQLException {

        String sql = """
                SELECT role_id
                FROM roles
                WHERE role_name = ?
                LIMIT 1
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, role.name());

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new SQLException(
                            "Selected staff role is not configured"
                    );
                }

                return resultSet.getLong("role_id");
            }
        }
    }

    private long insertUser(
            Connection connection,
            StaffMember staffMember,
            String passwordHash,
            long roleId
    ) throws SQLException {

        String sql = """
                INSERT INTO users (
                    username,
                    password_hash,
                    full_name,
                    email,
                    contact_number,
                    role_id,
                    active
                )
                VALUES (?, ?, ?, ?, ?, ?, TRUE)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            statement.setString(1, staffMember.username());
            statement.setString(2, passwordHash);
            statement.setString(3, staffMember.fullName());
            statement.setString(4, staffMember.email());
            statement.setString(5, staffMember.contactNumber());
            statement.setLong(6, roleId);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException(
                            "Staff user ID was not generated"
                    );
                }

                return keys.getLong(1);
            }
        }
    }

    private void insertDentist(
            Connection connection,
            long userId,
            StaffMember staffMember
    ) throws SQLException {

        String sql = """
                INSERT INTO dentists (
                    user_id,
                    registration_number,
                    specialization,
                    consultation_fee,
                    active
                )
                VALUES (?, ?, ?, ?, TRUE)
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, userId);
            statement.setString(
                    2,
                    staffMember.registrationNumber()
            );

            setNullableString(
                    statement,
                    3,
                    staffMember.specialization()
            );

            statement.setBigDecimal(
                    4,
                    staffMember.consultationFee()
            );

            statement.executeUpdate();
        }
    }

    private Optional<StaffMember> findById(
            Connection connection,
            long userId
    ) throws SQLException {

        String sql = SELECT_COLUMNS + """
                WHERE u.user_id = ?
                LIMIT 1
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, userId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next()
                        ? Optional.of(mapStaffMember(resultSet))
                        : Optional.empty();
            }
        }
    }

    private StaffMember mapStaffMember(ResultSet resultSet)
            throws SQLException {

        return new StaffMember(
                resultSet.getLong("user_id"),
                resultSet.getString("username"),
                resultSet.getString("full_name"),
                resultSet.getString("email"),
                resultSet.getString("contact_number"),
                Role.valueOf(resultSet.getString("role_name")),
                resultSet.getBoolean("active"),
                resultSet.getString("registration_number"),
                resultSet.getString("specialization"),
                resultSet.getBigDecimal("consultation_fee"),
                resultSet.getTimestamp("created_at")
                        .toLocalDateTime()
        );
    }

    private boolean valueExists(
            String sql,
            String value
    ) throws SQLException {

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, value);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next();
            }
        }
    }

    private void setNullableString(
            PreparedStatement statement,
            int index,
            String value
    ) throws SQLException {

        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    private void rollback(
            Connection connection,
            Exception originalException
    ) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            originalException.addSuppressed(rollbackException);
        }
    }
}
