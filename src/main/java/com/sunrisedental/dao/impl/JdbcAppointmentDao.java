package com.sunrisedental.dao.impl;

import com.sunrisedental.config.DatabaseConfig;
import com.sunrisedental.dao.AppointmentDao;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcAppointmentDao implements AppointmentDao {
    private static final String SELECT_DETAILS = """
            SELECT a.appointment_id, a.appointment_number,
                   a.patient_id, p.patient_code, p.full_name AS patient_name,
                   p.contact_number AS patient_contact,
                   a.dentist_id, u.full_name AS dentist_name,
                   a.treatment_type_id, t.treatment_name,
                   t.default_fee AS treatment_fee,
                   t.default_duration_minutes AS duration_minutes,
                   d.consultation_fee,
                   a.start_at, a.end_at, a.status,
                   a.patient_reason, a.internal_notes, a.cancellation_reason,
                   a.created_by, a.created_at, a.updated_at, a.version_number
            FROM appointments a
            INNER JOIN patients p ON p.patient_id = a.patient_id
            INNER JOIN dentists d ON d.dentist_id = a.dentist_id
            INNER JOIN users u ON u.user_id = d.user_id
            INNER JOIN treatment_types t
                    ON t.treatment_type_id = a.treatment_type_id
            """;

    @Override
    public Appointment create(Appointment appointment) throws SQLException {
        String sql = """
                INSERT INTO appointments (
                    appointment_number, patient_id, dentist_id,
                    treatment_type_id, start_at, end_at, status,
                    patient_reason, internal_notes, created_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        long appointmentId;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, appointment.appointmentNumber());
            statement.setLong(2, appointment.patientId());
            statement.setLong(3, appointment.dentistId());
            statement.setLong(4, appointment.treatmentTypeId());
            statement.setTimestamp(5, Timestamp.valueOf(appointment.startAt()));
            statement.setTimestamp(6, Timestamp.valueOf(appointment.endAt()));
            statement.setString(7, appointment.status().name());
            setNullableString(statement, 8, appointment.patientReason());
            setNullableString(statement, 9, appointment.internalNotes());
            statement.setLong(10, appointment.createdBy());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Appointment ID was not generated");
                }
                appointmentId = keys.getLong(1);
            }
        }

        return findById(appointmentId).orElseThrow(
                () -> new SQLException("Created appointment could not be retrieved"));
    }

    @Override
    public Optional<Appointment> findById(long appointmentId) throws SQLException {
        String sql = SELECT_DETAILS + " WHERE a.appointment_id = ? LIMIT 1";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, appointmentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Appointment> findByNumber(String appointmentNumber) throws SQLException {
        String sql = SELECT_DETAILS + " WHERE a.appointment_number = ? LIMIT 1";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, appointmentNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Appointment> search(
            String searchTerm, LocalDate date, AppointmentStatus status
    ) throws SQLException {
        String sql = SELECT_DETAILS + """
                WHERE (? = ''
                       OR LOWER(a.appointment_number) LIKE ?
                       OR LOWER(p.patient_code) LIKE ?
                       OR LOWER(p.full_name) LIKE ?
                       OR p.contact_number LIKE ?)
                  AND (? IS NULL OR DATE(a.start_at) = ?)
                  AND (? = '' OR a.status = ?)
                ORDER BY a.start_at DESC
                LIMIT 200
                """;

        String term = searchTerm == null ? "" : searchTerm.trim().toLowerCase();
        String pattern = "%" + term + "%";
        String statusValue = status == null ? "" : status.name();
        List<Appointment> appointments = new ArrayList<>();

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, term);
            statement.setString(2, pattern);
            statement.setString(3, pattern);
            statement.setString(4, pattern);
            statement.setString(5, pattern);
            if (date == null) {
                statement.setNull(6, Types.DATE);
                statement.setNull(7, Types.DATE);
            } else {
                statement.setDate(6, Date.valueOf(date));
                statement.setDate(7, Date.valueOf(date));
            }
            statement.setString(8, statusValue);
            statement.setString(9, statusValue);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    appointments.add(map(resultSet));
                }
            }
        }
        return appointments;
    }

    @Override
    public boolean hasDentistConflict(
            long dentistId, LocalDateTime startAt, LocalDateTime endAt,
            Long excludedAppointmentId
    ) throws SQLException {
        return hasConflict("dentist_id", dentistId, startAt, endAt,
                excludedAppointmentId);
    }

    @Override
    public boolean hasPatientConflict(
            long patientId, LocalDateTime startAt, LocalDateTime endAt,
            Long excludedAppointmentId
    ) throws SQLException {
        return hasConflict("patient_id", patientId, startAt, endAt,
                excludedAppointmentId);
    }

    private boolean hasConflict(
            String column, long entityId, LocalDateTime startAt,
            LocalDateTime endAt, Long excludedAppointmentId
    ) throws SQLException {
        String sql = """
                SELECT 1 FROM appointments
                WHERE %s = ?
                  AND status NOT IN ('CANCELLED', 'NO_SHOW')
                  AND start_at < ? AND end_at > ?
                  AND (? IS NULL OR appointment_id <> ?)
                LIMIT 1
                """.formatted(column);

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, entityId);
            statement.setTimestamp(2, Timestamp.valueOf(endAt));
            statement.setTimestamp(3, Timestamp.valueOf(startAt));
            if (excludedAppointmentId == null) {
                statement.setNull(4, Types.BIGINT);
                statement.setNull(5, Types.BIGINT);
            } else {
                statement.setLong(4, excludedAppointmentId);
                statement.setLong(5, excludedAppointmentId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    @Override
    public boolean updateSchedule(Appointment appointment, int expectedVersion)
            throws SQLException {
        String sql = """
                UPDATE appointments
                SET patient_id = ?, dentist_id = ?, treatment_type_id = ?,
                    start_at = ?, end_at = ?, patient_reason = ?,
                    internal_notes = ?, version_number = version_number + 1
                WHERE appointment_id = ? AND version_number = ?
                  AND status IN ('SCHEDULED', 'CONFIRMED')
                """;

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, appointment.patientId());
            statement.setLong(2, appointment.dentistId());
            statement.setLong(3, appointment.treatmentTypeId());
            statement.setTimestamp(4, Timestamp.valueOf(appointment.startAt()));
            statement.setTimestamp(5, Timestamp.valueOf(appointment.endAt()));
            setNullableString(statement, 6, appointment.patientReason());
            setNullableString(statement, 7, appointment.internalNotes());
            statement.setLong(8, appointment.appointmentId());
            statement.setInt(9, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    @Override
    public boolean updateStatus(
            long appointmentId, AppointmentStatus status,
            String cancellationReason, int expectedVersion
    ) throws SQLException {
        String sql = """
                UPDATE appointments
                SET status = ?, cancellation_reason = ?,
                    version_number = version_number + 1
                WHERE appointment_id = ? AND version_number = ?
                """;

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            setNullableString(statement, 2, cancellationReason);
            statement.setLong(3, appointmentId);
            statement.setInt(4, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    private Appointment map(ResultSet resultSet) throws SQLException {
        return new Appointment(
                resultSet.getLong("appointment_id"),
                resultSet.getString("appointment_number"),
                resultSet.getLong("patient_id"),
                resultSet.getString("patient_code"),
                resultSet.getString("patient_name"),
                resultSet.getString("patient_contact"),
                resultSet.getLong("dentist_id"),
                resultSet.getString("dentist_name"),
                resultSet.getLong("treatment_type_id"),
                resultSet.getString("treatment_name"),
                resultSet.getBigDecimal("treatment_fee"),
                resultSet.getInt("duration_minutes"),
                resultSet.getBigDecimal("consultation_fee"),
                resultSet.getTimestamp("start_at").toLocalDateTime(),
                resultSet.getTimestamp("end_at").toLocalDateTime(),
                AppointmentStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("patient_reason"),
                resultSet.getString("internal_notes"),
                resultSet.getString("cancellation_reason"),
                resultSet.getLong("created_by"),
                resultSet.getTimestamp("created_at").toLocalDateTime(),
                resultSet.getTimestamp("updated_at").toLocalDateTime(),
                resultSet.getInt("version_number")
        );
    }

    private void setNullableString(
            PreparedStatement statement, int index, String value
    ) throws SQLException {
        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }
}