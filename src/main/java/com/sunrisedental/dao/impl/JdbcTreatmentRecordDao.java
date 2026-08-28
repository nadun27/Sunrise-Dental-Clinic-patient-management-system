package com.sunrisedental.dao.impl;

import com.sunrisedental.config.DatabaseConfig;
import com.sunrisedental.dao.TreatmentRecordDao;
import com.sunrisedental.exception.NotFoundException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.TreatmentRecord;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcTreatmentRecordDao
        implements TreatmentRecordDao {

    private static final String SELECT_DETAILS = """
            SELECT tr.treatment_record_id,
                   tr.appointment_id,
                   a.appointment_number,
                   p.patient_code,
                   p.full_name AS patient_name,
                   tr.dentist_id,
                   u.full_name AS dentist_name,
                   tt.treatment_name,
                   tr.diagnosis,
                   tr.treatment_performed,
                   tr.clinical_notes,
                   tr.prescription,
                   tr.follow_up_date,
                   tr.created_at,
                   tr.updated_at
            FROM treatment_records tr
            INNER JOIN appointments a
                    ON a.appointment_id = tr.appointment_id
            INNER JOIN patients p
                    ON p.patient_id = a.patient_id
            INNER JOIN dentists d
                    ON d.dentist_id = tr.dentist_id
            INNER JOIN users u
                    ON u.user_id = d.user_id
            INNER JOIN treatment_types tt
                    ON tt.treatment_type_id = a.treatment_type_id
            """;

    @Override
    public TreatmentRecord completeSession(
            TreatmentRecord treatmentRecord,
            int expectedVersion
    ) throws SQLException {

        String lockAppointmentSql = """
                SELECT dentist_id, status, version_number
                FROM appointments
                WHERE appointment_id = ?
                FOR UPDATE
                """;

        String existingRecordSql = """
                SELECT 1
                FROM treatment_records
                WHERE appointment_id = ?
                LIMIT 1
                """;

        String insertSql = """
                INSERT INTO treatment_records (
                    appointment_id,
                    dentist_id,
                    diagnosis,
                    treatment_performed,
                    clinical_notes,
                    prescription,
                    follow_up_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        String completeAppointmentSql = """
                UPDATE appointments
                SET status = 'COMPLETED',
                    version_number = version_number + 1
                WHERE appointment_id = ?
                  AND status = 'IN_TREATMENT'
                  AND version_number = ?
                """;

        long treatmentRecordId;

        try (Connection connection =
                     DatabaseConfig.getConnection()) {

            connection.setAutoCommit(false);

            try {
                lockAndValidateAppointment(
                        connection,
                        lockAppointmentSql,
                        treatmentRecord,
                        expectedVersion
                );

                rejectExistingRecord(
                        connection,
                        existingRecordSql,
                        treatmentRecord.appointmentId()
                );

                treatmentRecordId = insertRecord(
                        connection,
                        insertSql,
                        treatmentRecord
                );

                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     completeAppointmentSql
                             )) {

                    statement.setLong(
                            1,
                            treatmentRecord.appointmentId()
                    );

                    statement.setInt(2, expectedVersion);

                    if (statement.executeUpdate() != 1) {
                        throw new ValidationException(
                                "The appointment was changed by " +
                                        "another user. Refresh and try again"
                        );
                    }
                }

                connection.commit();

            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;

            } finally {
                connection.setAutoCommit(true);
            }
        }

        return findById(treatmentRecordId)
                .orElseThrow(() ->
                        new SQLException(
                                "Created treatment record could not be retrieved"
                        )
                );
    }

    @Override
    public Optional<TreatmentRecord> findById(
            long treatmentRecordId
    ) throws SQLException {

        String sql = SELECT_DETAILS + """
                WHERE tr.treatment_record_id = ?
                LIMIT 1
                """;

        return findOne(sql, treatmentRecordId);
    }

    @Override
    public Optional<TreatmentRecord> findByAppointmentId(
            long appointmentId
    ) throws SQLException {

        String sql = SELECT_DETAILS + """
                WHERE tr.appointment_id = ?
                LIMIT 1
                """;

        return findOne(sql, appointmentId);
    }

    @Override
    public List<TreatmentRecord> search(
            String searchTerm
    ) throws SQLException {

        String sql = SELECT_DETAILS + """
                WHERE (? = ''
                       OR LOWER(a.appointment_number) LIKE ?
                       OR LOWER(p.patient_code) LIKE ?
                       OR LOWER(p.full_name) LIKE ?
                       OR LOWER(u.full_name) LIKE ?
                       OR LOWER(tt.treatment_name) LIKE ?
                       OR LOWER(tr.diagnosis) LIKE ?)
                ORDER BY tr.created_at DESC
                LIMIT 200
                """;

        String term = searchTerm == null
                ? ""
                : searchTerm.trim().toLowerCase();

        String pattern = "%" + term + "%";
        List<TreatmentRecord> records = new ArrayList<>();

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, term);

            for (int index = 2; index <= 7; index++) {
                statement.setString(index, pattern);
            }

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    records.add(map(resultSet));
                }
            }
        }

        return records;
    }

    private void lockAndValidateAppointment(
            Connection connection,
            String sql,
            TreatmentRecord treatmentRecord,
            int expectedVersion
    ) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(
                    1,
                    treatmentRecord.appointmentId()
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new NotFoundException(
                            "Appointment was not found"
                    );
                }

                String status = resultSet.getString("status");
                int version = resultSet.getInt("version_number");
                long dentistId = resultSet.getLong("dentist_id");

                if (!AppointmentStatus.IN_TREATMENT.name()
                        .equals(status)) {

                    throw new ValidationException(
                            "Only an appointment in treatment " +
                                    "can be completed"
                    );
                }

                if (version != expectedVersion) {
                    throw new ValidationException(
                            "The appointment was changed by " +
                                    "another user. Refresh and try again"
                    );
                }

                if (dentistId != treatmentRecord.dentistId()) {
                    throw new ValidationException(
                            "The treatment record must use the " +
                                    "appointment's assigned dentist"
                    );
                }
            }
        }
    }

    private void rejectExistingRecord(
            Connection connection,
            String sql,
            long appointmentId
    ) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setLong(1, appointmentId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (resultSet.next()) {
                    throw new ValidationException(
                            "A treatment record already exists " +
                                    "for this appointment"
                    );
                }
            }
        }
    }

    private long insertRecord(
            Connection connection,
            String sql,
            TreatmentRecord treatmentRecord
    ) throws SQLException {

        try (PreparedStatement statement =
                     connection.prepareStatement(
                             sql,
                             Statement.RETURN_GENERATED_KEYS
                     )) {

            statement.setLong(
                    1,
                    treatmentRecord.appointmentId()
            );

            statement.setLong(
                    2,
                    treatmentRecord.dentistId()
            );

            statement.setString(
                    3,
                    treatmentRecord.diagnosis()
            );

            statement.setString(
                    4,
                    treatmentRecord.treatmentPerformed()
            );

            setNullableString(
                    statement,
                    5,
                    treatmentRecord.clinicalNotes()
            );

            setNullableString(
                    statement,
                    6,
                    treatmentRecord.prescription()
            );

            if (treatmentRecord.followUpDate() == null) {
                statement.setNull(7, Types.DATE);
            } else {
                statement.setDate(
                        7,
                        Date.valueOf(
                                treatmentRecord.followUpDate()
                        )
                );
            }

            statement.executeUpdate();

            try (ResultSet keys =
                         statement.getGeneratedKeys()) {

                if (!keys.next()) {
                    throw new SQLException(
                            "Treatment record ID was not generated"
                    );
                }

                return keys.getLong(1);
            }
        }
    }

    private Optional<TreatmentRecord> findOne(
            String sql,
            long id
    ) throws SQLException {

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, id);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        }
    }

    private TreatmentRecord map(ResultSet resultSet)
            throws SQLException {

        Date followUpDate =
                resultSet.getDate("follow_up_date");

        return new TreatmentRecord(
                resultSet.getLong("treatment_record_id"),
                resultSet.getLong("appointment_id"),
                resultSet.getString("appointment_number"),
                resultSet.getString("patient_code"),
                resultSet.getString("patient_name"),
                resultSet.getLong("dentist_id"),
                resultSet.getString("dentist_name"),
                resultSet.getString("treatment_name"),
                resultSet.getString("diagnosis"),
                resultSet.getString("treatment_performed"),
                resultSet.getString("clinical_notes"),
                resultSet.getString("prescription"),
                followUpDate == null
                        ? null
                        : followUpDate.toLocalDate(),
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
}
