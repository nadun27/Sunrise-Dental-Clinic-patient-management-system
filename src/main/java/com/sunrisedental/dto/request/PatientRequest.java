package com.sunrisedental.dto.request;

import java.time.LocalDate;

public record PatientRequest(
        String fullName,
        String address,
        String contactNumber,
        String email,
        LocalDate dateOfBirth,
        String gender,
        String allergies,
        String medicalNotes
) {
}