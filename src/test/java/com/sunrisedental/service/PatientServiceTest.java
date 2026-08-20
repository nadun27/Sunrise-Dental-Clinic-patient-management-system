package com.sunrisedental.service;

import com.sunrisedental.dao.PatientDao;
import com.sunrisedental.dto.request.PatientRequest;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Patient;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PatientServiceTest {

    @Test
    void createsPatientWithNormalizedInformation()
            throws SQLException {

        FakePatientDao patientDao = new FakePatientDao();
        PatientService service =
                new PatientService(patientDao);

        PatientRequest request = new PatientRequest(
                "  Nimal   Perera ",
                "  12 Main Street, Colombo ",
                "077 123 4567",
                "nimal@example.com",
                LocalDate.of(1990, 5, 10),
                "male",
                "Penicillin",
                "Regular patient"
        );

        Patient patient = service.create(request, 1);

        assertEquals("Nimal Perera", patient.fullName());
        assertEquals(
                "0771234567",
                patient.contactNumber()
        );

        assertEquals("MALE", patient.gender());

        assertTrue(
                patient.patientCode().matches(
                        "PAT-\\d{8}-[A-Z2-9]{6}"
                )
        );
    }

    @Test
    void rejectsInvalidContactNumber() {

        PatientService service =
                new PatientService(
                        new FakePatientDao()
                );

        PatientRequest request = new PatientRequest(
                "Nimal Perera",
                "12 Main Street",
                "12345",
                null,
                null,
                null,
                null,
                null
        );

        ValidationException exception =
                assertThrows(
                        ValidationException.class,
                        () -> service.create(request, 1)
                );

        assertEquals(
                "Enter a valid Sri Lankan contact number",
                exception.getMessage()
        );
    }

    @Test
    void rejectsFutureDateOfBirth() {

        PatientService service =
                new PatientService(
                        new FakePatientDao()
                );

        PatientRequest request = new PatientRequest(
                "Nimal Perera",
                "12 Main Street",
                "0771234567",
                null,
                LocalDate.now().plusDays(1),
                "MALE",
                null,
                null
        );

        assertThrows(
                ValidationException.class,
                () -> service.create(request, 1)
        );
    }

    @Test
    void updatesExistingPatient()
            throws SQLException {

        FakePatientDao patientDao = new FakePatientDao();

        PatientService service =
                new PatientService(patientDao);

        Patient original = service.create(
                new PatientRequest(
                        "Nimal Perera",
                        "12 Main Street",
                        "0771234567",
                        null,
                        null,
                        "MALE",
                        null,
                        null
                ),
                1
        );

        Patient updated = service.update(
                original.patientId(),
                new PatientRequest(
                        "Nimal Perera",
                        "45 New Road",
                        "0712345678",
                        "new@example.com",
                        null,
                        "MALE",
                        null,
                        "Updated details"
                )
        );

        assertEquals("45 New Road", updated.address());
        assertEquals(
                "0712345678",
                updated.contactNumber()
        );
    }

    private static class FakePatientDao
            implements PatientDao {

        private final Map<Long, Patient> patients =
                new HashMap<>();

        private long nextId = 1;

        @Override
        public Patient create(Patient patient) {

            long patientId = nextId++;
            LocalDateTime now = LocalDateTime.now();

            Patient savedPatient = new Patient(
                    patientId,
                    patient.patientCode(),
                    patient.fullName(),
                    patient.address(),
                    patient.contactNumber(),
                    patient.email(),
                    patient.dateOfBirth(),
                    patient.gender(),
                    patient.allergies(),
                    patient.medicalNotes(),
                    patient.active(),
                    patient.createdBy(),
                    now,
                    now
            );

            patients.put(patientId, savedPatient);

            return savedPatient;
        }

        @Override
        public Optional<Patient> findById(
                long patientId
        ) {
            return Optional.ofNullable(
                    patients.get(patientId)
            );
        }

        @Override
        public Optional<Patient> findByCode(
                String patientCode
        ) {
            return patients.values()
                    .stream()
                    .filter(patient ->
                            patient.patientCode()
                                    .equals(patientCode)
                    )
                    .findFirst();
        }

        @Override
        public List<Patient> search(
                String searchTerm,
                boolean includeInactive
        ) {
            String term =
                    searchTerm == null
                            ? ""
                            : searchTerm.toLowerCase();

            return patients.values()
                    .stream()
                    .filter(patient ->
                            includeInactive ||
                                    patient.active()
                    )
                    .filter(patient ->
                            patient.patientCode()
                                    .toLowerCase()
                                    .contains(term) ||

                                    patient.fullName()
                                            .toLowerCase()
                                            .contains(term) ||

                                    patient.contactNumber()
                                            .contains(term)
                    )
                    .toList();
        }

        @Override
        public boolean update(Patient patient) {

            if (!patients.containsKey(
                    patient.patientId()
            )) {
                return false;
            }

            Patient updatedPatient = new Patient(
                    patient.patientId(),
                    patient.patientCode(),
                    patient.fullName(),
                    patient.address(),
                    patient.contactNumber(),
                    patient.email(),
                    patient.dateOfBirth(),
                    patient.gender(),
                    patient.allergies(),
                    patient.medicalNotes(),
                    patient.active(),
                    patient.createdBy(),
                    patient.createdAt(),
                    LocalDateTime.now()
            );

            patients.put(
                    patient.patientId(),
                    updatedPatient
            );

            return true;
        }

        @Override
        public boolean setActive(
                long patientId,
                boolean active
        ) {
            Patient patient = patients.get(patientId);

            if (patient == null) {
                return false;
            }

            patients.put(
                    patientId,
                    new Patient(
                            patient.patientId(),
                            patient.patientCode(),
                            patient.fullName(),
                            patient.address(),
                            patient.contactNumber(),
                            patient.email(),
                            patient.dateOfBirth(),
                            patient.gender(),
                            patient.allergies(),
                            patient.medicalNotes(),
                            active,
                            patient.createdBy(),
                            patient.createdAt(),
                            LocalDateTime.now()
                    )
            );

            return true;
        }
    }
}