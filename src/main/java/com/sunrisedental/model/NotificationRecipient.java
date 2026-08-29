package com.sunrisedental.model;

import java.time.LocalDateTime;

public record NotificationRecipient(
        long appointmentId,
        String patientName,
        String email,
        String appointmentNumber,
        String dentistName,
        String treatmentName,
        LocalDateTime appointmentStart
) {
}
