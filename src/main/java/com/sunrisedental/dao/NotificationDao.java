package com.sunrisedental.dao;

import com.sunrisedental.model.Notification;
import com.sunrisedental.model.NotificationRecipient;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationDao {

    Notification enqueue(Notification notification)
            throws SQLException;

    Optional<NotificationRecipient> findRecipient(
            long appointmentId
    ) throws SQLException;

    List<Notification> claimPending(
            int batchSize,
            int maximumAttempts
    ) throws SQLException;

    void markSent(
            long notificationId,
            LocalDateTime sentAt
    ) throws SQLException;

    void markFailed(
            long notificationId,
            LocalDateTime nextAttemptAt,
            String errorMessage
    ) throws SQLException;

    int recoverStaleProcessing(
            LocalDateTime staleBefore
    ) throws SQLException;
}
