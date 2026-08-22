package com.sunrisedental.dao.impl;

import com.sunrisedental.config.DatabaseConfig;
import com.sunrisedental.dao.TreatmentDao;
import com.sunrisedental.model.Treatment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcTreatmentDao implements TreatmentDao {

    private static final String SELECT_TREATMENT = """
            SELECT
                treatment_type_id,
                treatment_code,
                treatment_name,
                description,
                default_fee,
                default_duration_minutes,
                active
            FROM treatment_types
            """;

    @Override
    public List<Treatment> findActive()
            throws SQLException {

        String sql = SELECT_TREATMENT + """
                WHERE active = TRUE
                ORDER BY treatment_name
                """;

        List<Treatment> treatments =
                new ArrayList<>();

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql);

                ResultSet resultSet =
                        statement.executeQuery()
        ) {
            while (resultSet.next()) {
                treatments.add(
                        mapTreatment(resultSet)
                );
            }
        }

        return treatments;
    }

    @Override
    public Optional<Treatment> findById(
            long treatmentTypeId
    ) throws SQLException {

        String sql = SELECT_TREATMENT + """
                WHERE treatment_type_id = ?
                LIMIT 1
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(
                    1,
                    treatmentTypeId
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        mapTreatment(resultSet)
                );
            }
        }
    }

    private Treatment mapTreatment(
            ResultSet resultSet
    ) throws SQLException {

        return new Treatment(
                resultSet.getLong(
                        "treatment_type_id"
                ),

                resultSet.getString(
                        "treatment_code"
                ),

                resultSet.getString(
                        "treatment_name"
                ),

                resultSet.getString("description"),

                resultSet.getBigDecimal(
                        "default_fee"
                ),

                resultSet.getInt(
                        "default_duration_minutes"
                ),

                resultSet.getBoolean("active")
        );
    }
}