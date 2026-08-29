package com.sunrisedental.email;

import com.sunrisedental.dao.NotificationDao;
import com.sunrisedental.model.Notification;

import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

public class NotificationDispatcher {

    private static final int MAXIMUM_ATTEMPTS = 5;
    private final NotificationDao notificationDao;
    private final EmailSender emailSender;
    private final NotificationTemplateRenderer renderer;
    private final int batchSize;
    private final Clock clock;

    public NotificationDispatcher(
            NotificationDao notificationDao,
            EmailSender emailSender,
            NotificationTemplateRenderer renderer,
            int batchSize
    ) {
        this(
                notificationDao,
                emailSender,
                renderer,
                batchSize,
                Clock.systemDefaultZone()
        );
    }

    NotificationDispatcher(
            NotificationDao notificationDao,
            EmailSender emailSender,
            NotificationTemplateRenderer renderer,
            int batchSize,
            Clock clock
    ) {
        this.notificationDao = notificationDao;
        this.emailSender = emailSender;
        this.renderer = renderer;
        this.batchSize = Math.max(1, batchSize);
        this.clock = clock;
    }

    public DeliverySummary processBatch()
            throws SQLException {

        LocalDateTime now = LocalDateTime.now(clock);

        notificationDao.recoverStaleProcessing(
                now
        );

        List<Notification> notifications =
                notificationDao.claimPending(
                        batchSize,
                        MAXIMUM_ATTEMPTS
                );

        int sent = 0;
        int failed = 0;

        for (Notification notification : notifications) {
            try {
                EmailMessage message =
                        renderer.render(notification);

                emailSender.send(message);

                notificationDao.markSent(
                        notification.notificationId(),
                        LocalDateTime.now(clock)
                );

                sent++;

            } catch (Exception exception) {
                failed++;

                notificationDao.markFailed(
                        notification.notificationId(),
                        LocalDateTime.now(clock).plusMinutes(
                                retryDelayMinutes(
                                        notification.attemptCount()
                                )
                        ),
                        errorMessage(exception)
                );
            }
        }

        return new DeliverySummary(
                notifications.size(),
                sent,
                failed
        );
    }

    private long retryDelayMinutes(int attemptCount) {
        return switch (attemptCount) {
            case 1 -> 1;
            case 2 -> 5;
            case 3 -> 15;
            default -> 60;
        };
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();

        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    public record DeliverySummary(
            int processed,
            int sent,
            int failed
    ) {
    }
}
