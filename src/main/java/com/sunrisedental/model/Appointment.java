package com.sunrisedental.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Appointment(
        long appointmentId,
        String appointmentNumber,

        long patientId,
        String patientCode,
        String patientName,
        String patientContact,

        long dentistId,
        String dentistName,

        long treatmentTypeId,
        String treatmentName,
        BigDecimal treatmentFee,
        int durationMinutes,

        BigDecimal consultationFee,

        LocalDateTime startAt,
        LocalDateTime endAt,
        AppointmentStatus status,

        String patientReason,
        String internalNotes,
        String cancellationReason,

        long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int versionNumber
) {
}