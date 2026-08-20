package com.sunrisedental.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Patient(
        long patientId,
        String patientCode,
        String fullName,
        String address,
        String contactNumber,
        String email,
        LocalDate dateOfBirth,
        String gender,
        String allergies,
        String medicalNotes,
        boolean active,
        long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}