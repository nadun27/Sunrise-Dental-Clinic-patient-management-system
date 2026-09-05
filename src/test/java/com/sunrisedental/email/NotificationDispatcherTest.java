package com.sunrisedental.email;

import com.sunrisedental.dao.NotificationDao;
import com.sunrisedental.event.ClinicEventType;
import com.sunrisedental.model.Notification;
import com.sunrisedental.model.NotificationRecipient;
import com.sunrisedental.model.NotificationStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationDispatcherTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-29T06:30:00Z"),
            ZoneId.of("Asia/Colombo")
    );

    @Test
    void marksSuccessfullyDeliveredEmailAsSent()
            throws Exception {

        FakeNotificationDao dao =
                new FakeNotificationDao(notification());

        List<EmailMessage> delivered =
                new java.util.ArrayList<>();

        NotificationDispatcher dispatcher =
                new NotificationDispatcher(
                        dao,
                        delivered::add,
                        new NotificationTemplateRenderer(),
                        10,
                        CLOCK
                );

        NotificationDispatcher.DeliverySummary summary =
                dispatcher.processBatch();

        assertEquals(1, summary.sent());
        assertEquals(0, summary.failed());
        assertEquals(1, delivered.size());
        assertNotNull(dao.sentAt);
    }

    @Test
    void recordsFailureAndSchedulesRetry()
            throws Exception {

        FakeNotificationDao dao =
                new FakeNotificationDao(notification());

        NotificationDispatcher dispatcher =
                new NotificationDispatcher(
                        dao,
                        message -> {
                            throw new Exception(
                                    "SMTP unavailable"
                            );
                        },
                        new NotificationTemplateRenderer(),
                        10,
                        CLOCK
                );

        NotificationDispatcher.DeliverySummary summary =
                dispatcher.processBatch();

        assertEquals(0, summary.sent());
        assertEquals(1, summary.failed());
        assertEquals("SMTP unavailable", dao.lastError);
        assertTrue(
                dao.nextAttemptAt.isAfter(
                        LocalDateTime.now(CLOCK)
                )
        );
    }

    private Notification notification() {
        return new Notification(
                1,
                1,
                "patient@example.com",
                ClinicEventType.APPOINTMENT_CREATED.name(),
                Map.of(
                        "patientName", "Nimal Perera",
                        "appointmentNumber", "APT-TEST-001",
                        "dentistName", "Dr. Perera",
                        "treatmentName", "Dental Filling",
                        "appointmentStart", "2026-09-01T10:00:00"
                ),
                NotificationStatus.PROCESSING,
                1,
                LocalDateTime.now(CLOCK),
                null,
                null,
                LocalDateTime.now(CLOCK)
        );
    }

    private static class FakeNotificationDao
            implements NotificationDao {

        private final Notification notification;
        private LocalDateTime sentAt;
        private LocalDateTime nextAttemptAt;
        private String lastError;

        private FakeNotificationDao(
                Notification notification
        ) {
            this.notification = notification;
        }

        @Override
        public Notification enqueue(Notification notification) {
            return notification;
        }

        @Override
        public Optional<NotificationRecipient> findRecipient(
                long appointmentId
        ) {
            return Optional.empty();
        }

        @Override
        public List<Notification> claimPending(
                int batchSize,
                int maximumAttempts
        ) {
            return List.of(notification);
        }

        @Override
        public void markSent(
                long notificationId,
                LocalDateTime sentAt
        ) {
            this.sentAt = sentAt;
        }

        @Override
        public void markFailed(
                long notificationId,
                LocalDateTime nextAttemptAt,
                String errorMessage
        ) {
            this.nextAttemptAt = nextAttemptAt;
            lastError = errorMessage;
        }

        @Override
        public int recoverStaleProcessing(
                LocalDateTime staleBefore
        ) {
            return 0;
        }
    }
}
