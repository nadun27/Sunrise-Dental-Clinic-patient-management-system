package com.sunrisedental.dto.request;

import java.time.LocalDate;

public record TreatmentRecordRequest(
        long appointmentId,
        int versionNumber,
        String diagnosis,
        String treatmentPerformed,
        String clinicalNotes,
        String prescription,
        LocalDate followUpDate
) {
}
