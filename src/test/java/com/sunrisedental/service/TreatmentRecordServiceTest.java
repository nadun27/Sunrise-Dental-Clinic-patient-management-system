package com.sunrisedental.service;

import com.sunrisedental.dao.AppointmentDao;
import com.sunrisedental.dao.DentistDao;
import com.sunrisedental.dao.TreatmentRecordDao;
import com.sunrisedental.dto.request.TreatmentRecordRequest;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.Dentist;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.TreatmentRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TreatmentRecordServiceTest {

    @Test
    void startsCheckedInAppointment() throws Exception {
        FakeAppointmentDao appointmentDao =
                new FakeAppointmentDao(
                        AppointmentStatus.CHECKED_IN
                );

        TreatmentRecordService service =
                service(appointmentDao, 1);

        Appointment updated = service.startSession(
                1,
                3,
                50,
                Role.DENTIST
        );

        assertEquals(
                AppointmentStatus.IN_TREATMENT,
                updated.status()
        );

        assertEquals(4, updated.versionNumber());
    }

    @Test
    void rejectsStartUnlessPatientIsCheckedIn() {
        TreatmentRecordService service = service(
                new FakeAppointmentDao(
                        AppointmentStatus.CONFIRMED
                ),
                1
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.startSession(
                        1,
                        3,
                        50,
                        Role.DENTIST
                )
        );

        assertEquals(
                "Only a checked-in appointment can start treatment",
                exception.getMessage()
        );
    }

    @Test
    void completesSessionAndCreatesClinicalRecord()
            throws Exception {

        FakeAppointmentDao appointmentDao =
                new FakeAppointmentDao(
                        AppointmentStatus.IN_TREATMENT
                );

        TreatmentRecordService service =
                service(appointmentDao, 1);

        TreatmentRecord record = service.completeSession(
                request(LocalDate.now().plusDays(14)),
                50,
                Role.DENTIST
        );

        assertEquals("Dental caries", record.diagnosis());

        assertEquals(
                "Composite filling placed",
                record.treatmentPerformed()
        );

        assertEquals(1, record.dentistId());
    }

    @Test
    void rejectsBlankDiagnosis() {
        TreatmentRecordService service = service(
                new FakeAppointmentDao(
                        AppointmentStatus.IN_TREATMENT
                ),
                1
        );

        TreatmentRecordRequest request =
                new TreatmentRecordRequest(
                        1,
                        3,
                        " ",
                        "Composite filling placed",
                        null,
                        null,
                        null
                );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.completeSession(
                        request,
                        50,
                        Role.DENTIST
                )
        );

        assertEquals(
                "Diagnosis is required",
                exception.getMessage()
        );
    }

    @Test
    void rejectsPastFollowUpDate() {
        TreatmentRecordService service = service(
                new FakeAppointmentDao(
                        AppointmentStatus.IN_TREATMENT
                ),
                1
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.completeSession(
                        request(LocalDate.now().minusDays(1)),
                        50,
                        Role.DENTIST
                )
        );

        assertEquals(
                "Follow-up date cannot be in the past",
                exception.getMessage()
        );
    }

    @Test
    void blocksDentistWhoIsNotAssigned() {
        TreatmentRecordService service = service(
                new FakeAppointmentDao(
                        AppointmentStatus.IN_TREATMENT
                ),
                2
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.completeSession(
                        request(null),
                        50,
                        Role.DENTIST
                )
        );

        assertEquals(
                "Only the assigned dentist can manage " +
                        "this treatment session",
                exception.getMessage()
        );
    }

    @Test
    void allowsAdministratorToCompleteAssignedSession()
            throws Exception {

        TreatmentRecordService service = service(
                new FakeAppointmentDao(
                        AppointmentStatus.IN_TREATMENT
                ),
                2
        );

        TreatmentRecord record = service.completeSession(
                request(null),
                99,
                Role.ADMIN
        );

        assertEquals(1, record.treatmentRecordId());
        assertEquals(1, record.dentistId());
    }

    private TreatmentRecordService service(
            FakeAppointmentDao appointmentDao,
            long loggedInDentistId
    ) {

        return new TreatmentRecordService(
                new FakeTreatmentRecordDao(),
                appointmentDao,
                new FakeDentistDao(loggedInDentistId)
        );
    }

    private TreatmentRecordRequest request(
            LocalDate followUpDate
    ) {

        return new TreatmentRecordRequest(
                1,
                3,
                " Dental caries ",
                " Composite filling placed ",
                "Patient tolerated the procedure well",
                "Paracetamol when required",
                followUpDate
        );
    }

    private static class FakeTreatmentRecordDao
            implements TreatmentRecordDao {

        private TreatmentRecord record;

        @Override
        public TreatmentRecord completeSession(
                TreatmentRecord source,
                int expectedVersion
        ) {

            record = new TreatmentRecord(
                    1,
                    source.appointmentId(),
                    "APT-TEST-001",
                    "PAT-TEST-001",
                    "Nimal Perera",
                    source.dentistId(),
                    "Dr. Perera",
                    "Dental Filling",
                    source.diagnosis(),
                    source.treatmentPerformed(),
                    source.clinicalNotes(),
                    source.prescription(),
                    source.followUpDate(),
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );

            return record;
        }

        @Override
        public Optional<TreatmentRecord> findById(long id) {
            return record != null && record.treatmentRecordId() == id
                    ? Optional.of(record)
                    : Optional.empty();
        }

        @Override
        public Optional<TreatmentRecord> findByAppointmentId(
                long appointmentId
        ) {
            return record != null &&
                    record.appointmentId() == appointmentId
                    ? Optional.of(record)
                    : Optional.empty();
        }

        @Override
        public List<TreatmentRecord> search(String term) {
            return record == null
                    ? List.of()
                    : List.of(record);
        }
    }

    private static class FakeDentistDao
            implements DentistDao {

        private final Dentist dentist;

        private FakeDentistDao(long dentistId) {
            dentist = new Dentist(
                    dentistId,
                    "Dr. Perera",
                    "SLDC-001",
                    "General Dentistry",
                    new BigDecimal("2500.00"),
                    true
            );
        }

        @Override
        public List<Dentist> findActive() {
            return List.of(dentist);
        }

        @Override
        public Optional<Dentist> findById(long dentistId) {
            return dentist.dentistId() == dentistId
                    ? Optional.of(dentist)
                    : Optional.empty();
        }

        @Override
        public Optional<Dentist> findByUserId(long userId) {
            return userId == 50
                    ? Optional.of(dentist)
                    : Optional.empty();
        }
    }

    private static class FakeAppointmentDao
            implements AppointmentDao {

        private final List<Appointment> appointments =
                new ArrayList<>();

        private FakeAppointmentDao(
                AppointmentStatus status
        ) {
            appointments.add(appointment(status, 3));
        }

        @Override
        public Appointment create(Appointment appointment) {
            appointments.add(appointment);
            return appointment;
        }

        @Override
        public Optional<Appointment> findById(long id) {
            return appointments.stream()
                    .filter(item ->
                            item.appointmentId() == id
                    )
                    .findFirst();
        }

        @Override
        public Optional<Appointment> findByNumber(
                String number
        ) {
            return appointments.stream()
                    .filter(item ->
                            item.appointmentNumber()
                                    .equals(number)
                    )
                    .findFirst();
        }

        @Override
        public List<Appointment> search(
                String term,
                LocalDate date,
                AppointmentStatus status
        ) {
            return List.copyOf(appointments);
        }

        @Override
        public boolean hasDentistConflict(
                long dentistId,
                LocalDateTime startAt,
                LocalDateTime endAt,
                Long excludedAppointmentId
        ) {
            return false;
        }

        @Override
        public boolean hasPatientConflict(
                long patientId,
                LocalDateTime startAt,
                LocalDateTime endAt,
                Long excludedAppointmentId
        ) {
            return false;
        }

        @Override
        public boolean updateSchedule(
                Appointment appointment,
                int expectedVersion
        ) {
            return true;
        }

        @Override
        public boolean updateStatus(
                long appointmentId,
                AppointmentStatus status,
                String cancellationReason,
                int expectedVersion
        ) {
            Optional<Appointment> existing =
                    findById(appointmentId);

            if (existing.isEmpty() ||
                    existing.get().versionNumber() !=
                            expectedVersion) {
                return false;
            }

            appointments.clear();
            appointments.add(
                    appointment(
                            status,
                            expectedVersion + 1
                    )
            );

            return true;
        }

        private Appointment appointment(
                AppointmentStatus status,
                int version
        ) {
            return new Appointment(
                    1,
                    "APT-TEST-001",
                    1,
                    "PAT-TEST-001",
                    "Nimal Perera",
                    "0771234567",
                    1,
                    "Dr. Perera",
                    1,
                    "Dental Filling",
                    new BigDecimal("8000.00"),
                    60,
                    new BigDecimal("2500.00"),
                    LocalDateTime.now(),
                    LocalDateTime.now().plusHours(1),
                    status,
                    "Tooth pain",
                    null,
                    null,
                    1,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    version
            );
        }
    }
}
