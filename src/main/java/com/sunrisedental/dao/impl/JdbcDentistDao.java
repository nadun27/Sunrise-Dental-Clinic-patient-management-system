package com.sunrisedental.dao.impl;

import com.sunrisedental.config.DatabaseConfig;
import com.sunrisedental.dao.DentistDao;
import com.sunrisedental.model.Dentist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcDentistDao implements DentistDao {
    private static final String SELECT = """
            SELECT d.dentist_id, u.full_name, d.registration_number,
                   d.specialization, d.consultation_fee, d.active
            FROM dentists d
            INNER JOIN users u ON u.user_id = d.user_id
            """;

    @Override
    public List<Dentist> findActive() throws SQLException {
        String sql = SELECT + " WHERE d.active = TRUE AND u.active = TRUE ORDER BY u.full_name";
        List<Dentist> dentists = new ArrayList<>();

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                dentists.add(map(resultSet));
            }
        }
        return dentists;
    }

    @Override
    public Optional<Dentist> findById(long dentistId) throws SQLException {
        String sql = SELECT + " WHERE d.dentist_id = ? LIMIT 1";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, dentistId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Dentist> findByUserId(long userId)
            throws SQLException {

        String sql = SELECT +
                " WHERE d.user_id = ? LIMIT 1";

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, userId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        }
    }

    private Dentist map(ResultSet resultSet) throws SQLException {
        return new Dentist(
                resultSet.getLong("dentist_id"),
                resultSet.getString("full_name"),
                resultSet.getString("registration_number"),
                resultSet.getString("specialization"),
                resultSet.getBigDecimal("consultation_fee"),
                resultSet.getBoolean("active")
        );
    }
}
