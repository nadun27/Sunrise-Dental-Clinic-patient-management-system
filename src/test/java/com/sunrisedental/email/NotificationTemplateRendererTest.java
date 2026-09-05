package com.sunrisedental.email;

import com.sunrisedental.event.ClinicEventType;
import com.sunrisedental.model.Notification;
import com.sunrisedental.model.NotificationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationTemplateRendererTest {

    private final NotificationTemplateRenderer renderer =
            new NotificationTemplateRenderer();

    @Test
    void rendersAppointmentCreatedEmail() {
        EmailMessage message = renderer.render(
                notification(
                        ClinicEventType.APPOINTMENT_CREATED,
                        Map.of()
                )
        );

        assertEquals(
                "Appointment Registered - APT-TEST-001",
                message.subject()
        );

        assertTrue(
                message.body().contains(
                        "01 September 2026 at 10:00 AM"
                )
        );
    }

    @Test
    void rendersPaymentReceiptDetails() {
        EmailMessage message = renderer.render(
                notification(
                        ClinicEventType.PAYMENT_RECEIVED,
                        Map.of(
                                "receiptNumber",
                                "RCP-TEST-001",
                                "invoiceNumber",
                                "INV-TEST-001",
                                "amount",
                                "1000.00",
                                "paymentMethod",
                                "CARD",
                                "remainingBalance",
                                "3500.00"
                        )
                )
        );

        assertTrue(
                message.body().contains("LKR 1000.00")
        );

        assertTrue(
                message.body().contains("LKR 3500.00")
        );
    }

    @Test
    void rejectsUnknownTemplateCode() {
        Notification notification = new Notification(
                1,
                1,
                "patient@example.com",
                "UNKNOWN_TEMPLATE",
                baseValues(),
                NotificationStatus.PROCESSING,
                1,
                LocalDateTime.now(),
                null,
                null,
                LocalDateTime.now()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> renderer.render(notification)
        );
    }

    private Notification notification(
            ClinicEventType type,
            Map<String, String> extraValues
    ) {
        Map<String, String> values = baseValues();
        values.putAll(extraValues);

        return new Notification(
                1,
                1,
                "patient@example.com",
                type.name(),
                values,
                NotificationStatus.PROCESSING,
                1,
                LocalDateTime.now(),
                null,
                null,
                LocalDateTime.now()
        );
    }

    private Map<String, String> baseValues() {
        Map<String, String> values = new HashMap<>();

        values.put("patientName", "Nimal Perera");
        values.put("appointmentNumber", "APT-TEST-001");
        values.put("dentistName", "Dr. Perera");
        values.put("treatmentName", "Dental Filling");
        values.put(
                "appointmentStart",
                "2026-09-01T10:00:00"
        );

        return values;
    }
}
