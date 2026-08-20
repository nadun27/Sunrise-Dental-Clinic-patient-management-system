package com.sunrisedental.dao.impl;

import com.sunrisedental.config.DatabaseConfig;
import com.sunrisedental.dao.PatientDao;
import com.sunrisedental.model.Patient;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcPatientDao implements PatientDao {

    private static final String SELECT_COLUMNS = """
            SELECT
                patient_id,
                patient_code,
                full_name,
                address,
                contact_number,
                email,
                date_of_birth,
                gender,
                allergies,
                medical_notes,
                active,
                created_by,
                created_at,
                updated_at
            FROM patients
            """;

    @Override
    public Patient create(Patient patient) throws SQLException {

        String sql = """
                INSERT INTO patients (
                    patient_code,
                    full_name,
                    address,
                    contact_number,
                    email,
                    date_of_birth,
                    gender,
                    allergies,
                    medical_notes,
                    active,
                    created_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql,
                                Statement.RETURN_GENERATED_KEYS
                        )
        ) {
            statement.setString(1, patient.patientCode());
            statement.setString(2, patient.fullName());
            statement.setString(3, patient.address());
            statement.setString(4, patient.contactNumber());

            setNullableString(statement, 5, patient.email());
            setNullableDate(statement, 6, patient.dateOfBirth());
            setNullableString(statement, 7, patient.gender());
            setNullableString(statement, 8, patient.allergies());
            setNullableString(statement, 9, patient.medicalNotes());

            statement.setBoolean(10, patient.active());
            statement.setLong(11, patient.createdBy());

            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException(
                            "Patient ID was not generated"
                    );
                }

                long patientId = keys.getLong(1);

                return findById(patientId)
                        .orElseThrow(() -> new SQLException(
                                "Created patient could not be retrieved"
                        ));
            }
        }
    }

    @Override
    public Optional<Patient> findById(long patientId)
            throws SQLException {

        String sql = SELECT_COLUMNS + """
                WHERE patient_id = ?
                LIMIT 1
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, patientId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapPatient(resultSet));
            }
        }
    }

    @Override
    public Optional<Patient> findByCode(String patientCode)
            throws SQLException {

        String sql = SELECT_COLUMNS + """
                WHERE patient_code = ?
                LIMIT 1
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, patientCode);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapPatient(resultSet));
            }
        }
    }

    @Override
    public List<Patient> search(
            String searchTerm,
            boolean includeInactive
    ) throws SQLException {

        String sql = SELECT_COLUMNS + """
                WHERE (? = TRUE OR active = TRUE)
                  AND (
                      ? = ''
                      OR LOWER(patient_code) LIKE ?
                      OR LOWER(full_name) LIKE ?
                      OR contact_number LIKE ?
                  )
                ORDER BY active DESC, full_name ASC
                LIMIT 100
                """;

        String normalizedTerm =
                searchTerm == null
                        ? ""
                        : searchTerm.trim().toLowerCase();

        String searchPattern = "%" + normalizedTerm + "%";

        List<Patient> patients = new ArrayList<>();

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setBoolean(1, includeInactive);
            statement.setString(2, normalizedTerm);
            statement.setString(3, searchPattern);
            statement.setString(4, searchPattern);
            statement.setString(5, searchPattern);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    patients.add(mapPatient(resultSet));
                }
            }
        }

        return patients;
    }

    @Override
    public boolean update(Patient patient)
            throws SQLException {

        String sql = """
                UPDATE patients
                SET full_name = ?,
                    address = ?,
                    contact_number = ?,
                    email = ?,
                    date_of_birth = ?,
                    gender = ?,
                    allergies = ?,
                    medical_notes = ?
                WHERE patient_id = ?
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, patient.fullName());
            statement.setString(2, patient.address());
            statement.setString(3, patient.contactNumber());

            setNullableString(statement, 4, patient.email());
            setNullableDate(statement, 5, patient.dateOfBirth());
            setNullableString(statement, 6, patient.gender());
            setNullableString(statement, 7, patient.allergies());
            setNullableString(statement, 8, patient.medicalNotes());

            statement.setLong(9, patient.patientId());

            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public boolean setActive(
            long patientId,
            boolean active
    ) throws SQLException {

        String sql = """
                UPDATE patients
                SET active = ?
                WHERE patient_id = ?
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setBoolean(1, active);
            statement.setLong(2, patientId);

            return statement.executeUpdate() == 1;
        }
    }

    private Patient mapPatient(ResultSet resultSet)
            throws SQLException {

        Date dateOfBirth = resultSet.getDate("date_of_birth");

        return new Patient(
                resultSet.getLong("patient_id"),
                resultSet.getString("patient_code"),
                resultSet.getString("full_name"),
                resultSet.getString("address"),
                resultSet.getString("contact_number"),
                resultSet.getString("email"),
                dateOfBirth == null
                        ? null
                        : dateOfBirth.toLocalDate(),
                resultSet.getString("gender"),
                resultSet.getString("allergies"),
                resultSet.getString("medical_notes"),
                resultSet.getBoolean("active"),
                resultSet.getLong("created_by"),
                resultSet.getTimestamp("created_at")
                        .toLocalDateTime(),
                resultSet.getTimestamp("updated_at")
                        .toLocalDateTime()
        );
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

    private void setNullableDate(
            PreparedStatement statement,
            int index,
            java.time.LocalDate value
    ) throws SQLException {

        if (value == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(value));
        }
    }
}