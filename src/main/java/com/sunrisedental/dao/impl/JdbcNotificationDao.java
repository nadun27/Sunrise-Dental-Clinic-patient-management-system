package com.sunrisedental.dao.impl;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sunrisedental.config.DatabaseConfig;
import com.sunrisedental.dao.NotificationDao;
import com.sunrisedental.model.Notification;
import com.sunrisedental.model.NotificationRecipient;
import com.sunrisedental.model.NotificationStatus;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcNotificationDao
        implements NotificationDao {

    private static final Gson GSON = new Gson();

    private static final Type PAYLOAD_TYPE =
            new TypeToken<Map<String, String>>() {
            }.getType();

    private static final String SELECT_NOTIFICATION = """
            SELECT notification_id,
                   appointment_id,
                   recipient,
                   template_code,
                   payload_json,
                   status,
                   attempt_count,
                   next_attempt_at,
                   sent_at,
                   last_error,
                   created_at
            FROM notification_outbox
            """;

    @Override
    public Notification enqueue(Notification notification)
            throws SQLException {

        String sql = """
                INSERT INTO notification_outbox (
                    appointment_id,
                    channel,
                    recipient,
                    template_code,
                    payload_json,
                    status,
                    attempt_count,
                    next_attempt_at
                ) VALUES (?, 'EMAIL', ?, ?, ?, 'PENDING', 0, ?)
                """;

        long notificationId;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql,
                                Statement.RETURN_GENERATED_KEYS
                        )
        ) {
            statement.setLong(
                    1,
                    notification.appointmentId()
            );

            statement.setString(
                    2,
                    notification.recipient()
            );

            statement.setString(
                    3,
                    notification.templateCode()
            );

            statement.setString(
                    4,
                    GSON.toJson(notification.payload())
            );

            statement.setTimestamp(
                    5,
                    Timestamp.valueOf(
                            notification.nextAttemptAt()
                    )
            );

            statement.executeUpdate();

            try (ResultSet keys =
                         statement.getGeneratedKeys()) {

                if (!keys.next()) {
                    throw new SQLException(
                            "Notification ID was not generated"
                    );
                }

                notificationId = keys.getLong(1);
            }
        }

        return findById(notificationId)
                .orElseThrow(() ->
                        new SQLException(
                                "Queued notification could not be retrieved"
                        )
                );
    }

    @Override
    public Optional<NotificationRecipient> findRecipient(
            long appointmentId
    ) throws SQLException {

        String sql = """
                SELECT a.appointment_id,
                       p.full_name AS patient_name,
                       p.email,
                       a.appointment_number,
                       u.full_name AS dentist_name,
                       tt.treatment_name,
                       a.start_at
                FROM appointments a
                INNER JOIN patients p
                        ON p.patient_id = a.patient_id
                INNER JOIN dentists d
                        ON d.dentist_id = a.dentist_id
                INNER JOIN users u
                        ON u.user_id = d.user_id
                INNER JOIN treatment_types tt
                        ON tt.treatment_type_id = a.treatment_type_id
                WHERE a.appointment_id = ?
                LIMIT 1
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, appointmentId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        new NotificationRecipient(
                                resultSet.getLong(
                                        "appointment_id"
                                ),
                                resultSet.getString(
                                        "patient_name"
                                ),
                                resultSet.getString("email"),
                                resultSet.getString(
                                        "appointment_number"
                                ),
                                resultSet.getString(
                                        "dentist_name"
                                ),
                                resultSet.getString(
                                        "treatment_name"
                                ),
                                resultSet.getTimestamp(
                                        "start_at"
                                ).toLocalDateTime()
                        )
                );
            }
        }
    }

    @Override
    public List<Notification> claimPending(
            int batchSize,
            int maximumAttempts
    ) throws SQLException {

        String selectSql = SELECT_NOTIFICATION + """
                WHERE status IN ('PENDING', 'FAILED')
                  AND next_attempt_at <= CURRENT_TIMESTAMP
                  AND attempt_count < ?
                ORDER BY created_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """;

        String updateSql = """
                UPDATE notification_outbox
                SET status = 'PROCESSING',
                    attempt_count = attempt_count + 1,
                    next_attempt_at = DATE_ADD(
                        CURRENT_TIMESTAMP,
                        INTERVAL 5 MINUTE
                    )
                WHERE notification_id = ?
                """;

        List<Notification> claimed = new ArrayList<>();

        try (Connection connection =
                     DatabaseConfig.getConnection()) {

            connection.setAutoCommit(false);

            try {
                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     selectSql
                             )) {

                    statement.setInt(1, maximumAttempts);
                    statement.setInt(2, batchSize);

                    try (ResultSet resultSet =
                                 statement.executeQuery()) {

                        while (resultSet.next()) {
                            claimed.add(map(resultSet));
                        }
                    }
                }

                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     updateSql
                             )) {

                    for (Notification notification : claimed) {
                        statement.setLong(
                                1,
                                notification.notificationId()
                        );

                        statement.addBatch();
                    }

                    statement.executeBatch();
                }

                connection.commit();

            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;

            } finally {
                connection.setAutoCommit(true);
            }
        }

        return claimed.stream()
                .map(this::asProcessing)
                .toList();
    }

    @Override
    public void markSent(
            long notificationId,
            LocalDateTime sentAt
    ) throws SQLException {

        String sql = """
                UPDATE notification_outbox
                SET status = 'SENT',
                    sent_at = ?,
                    last_error = NULL
                WHERE notification_id = ?
                  AND status = 'PROCESSING'
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setTimestamp(
                    1,
                    Timestamp.valueOf(sentAt)
            );

            statement.setLong(2, notificationId);
            statement.executeUpdate();
        }
    }

    @Override
    public void markFailed(
            long notificationId,
            LocalDateTime nextAttemptAt,
            String errorMessage
    ) throws SQLException {

        String sql = """
                UPDATE notification_outbox
                SET status = 'FAILED',
                    next_attempt_at = ?,
                    last_error = ?
                WHERE notification_id = ?
                  AND status = 'PROCESSING'
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setTimestamp(
                    1,
                    Timestamp.valueOf(nextAttemptAt)
            );

            setNullableString(
                    statement,
                    2,
                    truncate(errorMessage, 500)
            );

            statement.setLong(3, notificationId);
            statement.executeUpdate();
        }
    }

    @Override
    public int recoverStaleProcessing(
            LocalDateTime staleBefore
    ) throws SQLException {

        String sql = """
                UPDATE notification_outbox
                SET status = 'FAILED',
                    next_attempt_at = CURRENT_TIMESTAMP,
                    last_error = 'Recovered after interrupted delivery'
                WHERE status = 'PROCESSING'
                  AND next_attempt_at < ?
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setTimestamp(
                    1,
                    Timestamp.valueOf(staleBefore)
            );

            return statement.executeUpdate();
        }
    }

    private Optional<Notification> findById(
            long notificationId
    ) throws SQLException {

        String sql = SELECT_NOTIFICATION + """
                WHERE notification_id = ?
                LIMIT 1
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, notificationId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        }
    }

    private Notification map(ResultSet resultSet)
            throws SQLException {

        Timestamp sentAt = resultSet.getTimestamp("sent_at");

        Map<String, String> payload = GSON.fromJson(
                resultSet.getString("payload_json"),
                PAYLOAD_TYPE
        );

        return new Notification(
                resultSet.getLong("notification_id"),
                resultSet.getLong("appointment_id"),
                resultSet.getString("recipient"),
                resultSet.getString("template_code"),
                payload,
                NotificationStatus.valueOf(
                        resultSet.getString("status")
                ),
                resultSet.getInt("attempt_count"),
                resultSet.getTimestamp("next_attempt_at")
                        .toLocalDateTime(),
                sentAt == null
                        ? null
                        : sentAt.toLocalDateTime(),
                resultSet.getString("last_error"),
                resultSet.getTimestamp("created_at")
                        .toLocalDateTime()
        );
    }

    private Notification asProcessing(
            Notification notification
    ) {
        return new Notification(
                notification.notificationId(),
                notification.appointmentId(),
                notification.recipient(),
                notification.templateCode(),
                notification.payload(),
                NotificationStatus.PROCESSING,
                notification.attemptCount() + 1,
                notification.nextAttemptAt(),
                notification.sentAt(),
                notification.lastError(),
                notification.createdAt()
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

    private String truncate(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return "Unknown email delivery error";
        }

        String normalized = value.trim();

        return normalized.length() <= maximumLength
                ? normalized
                : normalized.substring(0, maximumLength);
    }
}
