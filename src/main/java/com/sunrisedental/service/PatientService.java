package com.sunrisedental.service;

import com.sunrisedental.dao.PatientDao;
import com.sunrisedental.dto.request.PatientRequest;
import com.sunrisedental.exception.NotFoundException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Patient;
import com.sunrisedental.util.PatientCodeGenerator;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class PatientService {

    private static final Pattern NAME_PATTERN =
            Pattern.compile(
                    "^[\\p{L}][\\p{L} .'-]{1,99}$"
            );

    private static final Pattern CONTACT_PATTERN =
            Pattern.compile(
                    "^(?:\\+94[1-9]\\d{8}|0[1-9]\\d{8})$"
            );

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile(
                    "^[A-Za-z0-9._%+-]+@" +
                            "[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
            );

    private static final Set<String> ALLOWED_GENDERS =
            Set.of(
                    "MALE",
                    "FEMALE",
                    "OTHER",
                    "PREFER_NOT_TO_SAY"
            );

    private final PatientDao patientDao;

    public PatientService(PatientDao patientDao) {
        this.patientDao = patientDao;
    }

    public Patient create(
            PatientRequest request,
            long createdBy
    ) throws SQLException {

        if (createdBy <= 0) {
            throw new ValidationException(
                    "Valid staff user is required"
            );
        }

        PatientRequest validRequest =
                validateAndNormalize(request);

        String patientCode = generateUniqueCode();

        Patient patient = new Patient(
                0,
                patientCode,
                validRequest.fullName(),
                validRequest.address(),
                validRequest.contactNumber(),
                validRequest.email(),
                validRequest.dateOfBirth(),
                validRequest.gender(),
                validRequest.allergies(),
                validRequest.medicalNotes(),
                true,
                createdBy,
                null,
                null
        );

        return patientDao.create(patient);
    }

    public Patient getById(long patientId)
            throws SQLException {

        validatePatientId(patientId);

        return patientDao.findById(patientId)
                .orElseThrow(() -> new NotFoundException(
                        "Patient was not found"
                ));
    }

    public Patient getByCode(String patientCode)
            throws SQLException {

        if (patientCode == null ||
                patientCode.isBlank()) {

            throw new ValidationException(
                    "Patient code is required"
            );
        }

        return patientDao.findByCode(
                        patientCode.trim().toUpperCase()
                )
                .orElseThrow(() -> new NotFoundException(
                        "Patient was not found"
                ));
    }

    public List<Patient> search(
            String searchTerm,
            boolean includeInactive
    ) throws SQLException {

        String normalizedTerm =
                searchTerm == null
                        ? ""
                        : searchTerm.trim();

        if (normalizedTerm.length() > 100) {
            throw new ValidationException(
                    "Search term cannot exceed 100 characters"
            );
        }

        return patientDao.search(
                normalizedTerm,
                includeInactive
        );
    }

    public Patient update(
            long patientId,
            PatientRequest request
    ) throws SQLException {

        Patient existingPatient = getById(patientId);

        PatientRequest validRequest =
                validateAndNormalize(request);

        Patient updatedPatient = new Patient(
                existingPatient.patientId(),
                existingPatient.patientCode(),
                validRequest.fullName(),
                validRequest.address(),
                validRequest.contactNumber(),
                validRequest.email(),
                validRequest.dateOfBirth(),
                validRequest.gender(),
                validRequest.allergies(),
                validRequest.medicalNotes(),
                existingPatient.active(),
                existingPatient.createdBy(),
                existingPatient.createdAt(),
                existingPatient.updatedAt()
        );

        boolean updated =
                patientDao.update(updatedPatient);

        if (!updated) {
            throw new NotFoundException(
                    "Patient was not found"
            );
        }

        return getById(patientId);
    }

    public void setActive(
            long patientId,
            boolean active
    ) throws SQLException {

        getById(patientId);

        boolean updated =
                patientDao.setActive(patientId, active);

        if (!updated) {
            throw new NotFoundException(
                    "Patient was not found"
            );
        }
    }

    private PatientRequest validateAndNormalize(
            PatientRequest request
    ) {
        if (request == null) {
            throw new ValidationException(
                    "Patient information is required"
            );
        }

        String fullName =
                normalizeRequired(
                        request.fullName(),
                        "Patient name",
                        2,
                        100
                );

        if (!NAME_PATTERN.matcher(fullName).matches()) {
            throw new ValidationException(
                    "Patient name contains invalid characters"
            );
        }

        String address =
                normalizeRequired(
                        request.address(),
                        "Address",
                        5,
                        255
                );

        String contactNumber =
                normalizeContact(request.contactNumber());

        String email =
                normalizeOptional(
                        request.email(),
                        150,
                        "Email"
                );

        if (email != null &&
                !EMAIL_PATTERN.matcher(email).matches()) {

            throw new ValidationException(
                    "Enter a valid email address"
            );
        }

        validateDateOfBirth(request.dateOfBirth());

        String gender =
                normalizeOptional(
                        request.gender(),
                        20,
                        "Gender"
                );

        if (gender != null) {
            gender = gender.toUpperCase();

            if (!ALLOWED_GENDERS.contains(gender)) {
                throw new ValidationException(
                        "Select a valid gender"
                );
            }
        }

        String allergies =
                normalizeOptional(
                        request.allergies(),
                        500,
                        "Allergies"
                );

        String medicalNotes =
                normalizeOptional(
                        request.medicalNotes(),
                        5000,
                        "Medical notes"
                );

        return new PatientRequest(
                fullName,
                address,
                contactNumber,
                email,
                request.dateOfBirth(),
                gender,
                allergies,
                medicalNotes
        );
    }

    private String generateUniqueCode()
            throws SQLException {

        for (int attempt = 0; attempt < 5; attempt++) {

            String patientCode =
                    PatientCodeGenerator.generate();

            if (patientDao.findByCode(patientCode).isEmpty()) {
                return patientCode;
            }
        }

        throw new SQLException(
                "A unique patient code could not be generated"
        );
    }

    private String normalizeRequired(
            String value,
            String fieldName,
            int minimumLength,
            int maximumLength
    ) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    fieldName + " is required"
            );
        }

        String normalized =
                value.trim().replaceAll("\\s+", " ");

        if (normalized.length() < minimumLength ||
                normalized.length() > maximumLength) {

            throw new ValidationException(
                    fieldName + " must contain between " +
                            minimumLength + " and " +
                            maximumLength + " characters"
            );
        }

        return normalized;
    }

    private String normalizeOptional(
            String value,
            int maximumLength,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (normalized.length() > maximumLength) {
            throw new ValidationException(
                    fieldName + " cannot exceed " +
                            maximumLength + " characters"
            );
        }

        return normalized;
    }

    private String normalizeContact(String value) {

        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    "Contact number is required"
            );
        }

        String normalized =
                value.replaceAll("[\\s()-]", "");

        if (!CONTACT_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException(
                    "Enter a valid Sri Lankan contact number"
            );
        }

        return normalized;
    }

    private void validateDateOfBirth(
            LocalDate dateOfBirth
    ) {
        if (dateOfBirth == null) {
            return;
        }

        LocalDate today = LocalDate.now();

        if (dateOfBirth.isAfter(today)) {
            throw new ValidationException(
                    "Date of birth cannot be in the future"
            );
        }

        if (dateOfBirth.isBefore(today.minusYears(120))) {
            throw new ValidationException(
                    "Date of birth is outside the allowed range"
            );
        }
    }

    private void validatePatientId(long patientId) {
        if (patientId <= 0) {
            throw new ValidationException(
                    "Invalid patient ID"
            );
        }
    }
}