package com.sunrisedental.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TreatmentRecord(
        long treatmentRecordId,
        long appointmentId,
        String appointmentNumber,
        String patientCode,
        String patientName,
        long dentistId,
        String dentistName,
        String treatmentName,
        String diagnosis,
        String treatmentPerformed,
        String clinicalNotes,
        String prescription,
        LocalDate followUpDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
