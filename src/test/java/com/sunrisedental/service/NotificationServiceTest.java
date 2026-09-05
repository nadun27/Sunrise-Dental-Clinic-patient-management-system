package com.sunrisedental.service;

import com.sunrisedental.dao.NotificationDao;
import com.sunrisedental.event.ClinicEvent;
import com.sunrisedental.event.ClinicEventType;
import com.sunrisedental.model.Notification;
import com.sunrisedental.model.NotificationRecipient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceTest {

    @Test
    void queuesAppointmentEmailWithRecipientContext()
            throws Exception {

        FakeNotificationDao dao =
                new FakeNotificationDao("patient@example.com");

        NotificationService service =
                new NotificationService(dao);

        Optional<Notification> result = service.queue(
                new ClinicEvent(
                        ClinicEventType.APPOINTMENT_CREATED,
                        1,
                        Map.of()
                )
        );

        assertTrue(result.isPresent());

        assertEquals(
                "patient@example.com",
                result.orElseThrow().recipient()
        );

        assertEquals(
                "Nimal Perera",
                result.orElseThrow()
                        .payload()
                        .get("patientName")
        );
    }

    @Test
    void mergesEventSpecificValuesIntoPayload()
            throws Exception {

        NotificationService service =
                new NotificationService(
                        new FakeNotificationDao(
                                "patient@example.com"
                        )
                );

        Notification notification = service.queue(
                new ClinicEvent(
                        ClinicEventType.BILL_CREATED,
                        1,
                        Map.of(
                                "invoiceNumber",
                                "INV-TEST-001",
                                "totalAmount",
                                "10500.00"
                        )
                )
        ).orElseThrow();

        assertEquals(
                "INV-TEST-001",
                notification.payload().get("invoiceNumber")
        );
    }

    @Test
    void skipsNotificationWhenPatientHasNoEmail()
            throws Exception {

        FakeNotificationDao dao =
                new FakeNotificationDao(null);

        NotificationService service =
                new NotificationService(dao);

        Optional<Notification> result = service.queue(
                new ClinicEvent(
                        ClinicEventType.APPOINTMENT_CREATED,
                        1,
                        Map.of()
                )
        );

        assertTrue(result.isEmpty());
        assertTrue(dao.queued.isEmpty());
    }

    private static class FakeNotificationDao
            implements NotificationDao {

        private final String email;
        private final List<Notification> queued =
                new java.util.ArrayList<>();

        private FakeNotificationDao(String email) {
            this.email = email;
        }

        @Override
        public Notification enqueue(Notification source) {
            Notification saved = new Notification(
                    queued.size() + 1,
                    source.appointmentId(),
                    source.recipient(),
                    source.templateCode(),
                    source.payload(),
                    source.status(),
                    source.attemptCount(),
                    source.nextAttemptAt(),
                    source.sentAt(),
                    source.lastError(),
                    LocalDateTime.now()
            );

            queued.add(saved);
            return saved;
        }

        @Override
        public Optional<NotificationRecipient> findRecipient(
                long appointmentId
        ) {
            return Optional.of(
                    new NotificationRecipient(
                            1,
                            "Nimal Perera",
                            email,
                            "APT-TEST-001",
                            "Dr. Perera",
                            "Dental Filling",
                            LocalDateTime.of(
                                    2026,
                                    9,
                                    1,
                                    10,
                                    0
                            )
                    )
            );
        }

        @Override
        public List<Notification> claimPending(
                int batchSize,
                int maximumAttempts
        ) {
            return List.of();
        }

        @Override
        public void markSent(
                long notificationId,
                LocalDateTime sentAt
        ) {
        }

        @Override
        public void markFailed(
                long notificationId,
                LocalDateTime nextAttemptAt,
                String errorMessage
        ) {
        }

        @Override
        public int recoverStaleProcessing(
                LocalDateTime staleBefore
        ) {
            return 0;
        }
    }
}
