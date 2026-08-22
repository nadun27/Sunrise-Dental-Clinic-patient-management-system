package com.sunrisedental.dto.request;

import java.time.LocalDateTime;

public record AppointmentRequest(
        long patientId,
        long dentistId,
        long treatmentTypeId,
        LocalDateTime startAt,
        String patientReason,
        String internalNotes
) {
}