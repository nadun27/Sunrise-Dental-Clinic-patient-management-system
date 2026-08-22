package com.sunrisedental.service;

import com.sunrisedental.dao.AppointmentDao;
import com.sunrisedental.dao.DentistDao;
import com.sunrisedental.dao.PatientDao;
import com.sunrisedental.dao.TreatmentDao;
import com.sunrisedental.dto.request.AppointmentRequest;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.Dentist;
import com.sunrisedental.model.Patient;
import com.sunrisedental.model.Treatment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppointmentServiceTest {
    @Test
    void createsAppointmentAndCalculatesEndTime() throws SQLException {
        FakeAppointmentDao appointmentDao = new FakeAppointmentDao();
        AppointmentService service = service(appointmentDao);
        LocalDateTime start = futureWorkingDay();

        Appointment appointment = service.create(request(start), 1);

        assertEquals(start.plusMinutes(60), appointment.endAt());
        assertEquals(AppointmentStatus.SCHEDULED, appointment.status());
    }

    @Test
    void rejectsDentistDoubleBooking() {
        FakeAppointmentDao appointmentDao = new FakeAppointmentDao();
        appointmentDao.dentistConflict = true;
        AppointmentService service = service(appointmentDao);

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.create(request(futureWorkingDay()), 1));

        assertEquals("The dentist already has an appointment during this time",
                exception.getMessage());
    }

    @Test
    void rejectsSundayAppointment() {
        AppointmentService service = service(new FakeAppointmentDao());
        LocalDateTime sunday = LocalDate.now()
                .with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
                .atTime(10, 0);

        assertThrows(ValidationException.class,
                () -> service.create(request(sunday), 1));
    }

    @Test
    void followsValidStatusTransition() throws SQLException {
        FakeAppointmentDao appointmentDao = new FakeAppointmentDao();
        AppointmentService service = service(appointmentDao);
        Appointment created = service.create(request(futureWorkingDay()), 1);

        Appointment confirmed = service.changeStatus(
                created.appointmentId(), "CONFIRMED", null,
                created.versionNumber());

        assertEquals(AppointmentStatus.CONFIRMED, confirmed.status());
    }

    @Test
    void rejectsInvalidStatusTransition() throws SQLException {
        FakeAppointmentDao appointmentDao = new FakeAppointmentDao();
        AppointmentService service = service(appointmentDao);
        Appointment created = service.create(request(futureWorkingDay()), 1);

        assertThrows(ValidationException.class,
                () -> service.changeStatus(
                        created.appointmentId(), "COMPLETED", null,
                        created.versionNumber()));
    }

    private AppointmentService service(FakeAppointmentDao appointmentDao) {
        return new AppointmentService(
                appointmentDao,
                new FakePatientDao(),
                new FakeDentistDao(),
                new FakeTreatmentDao());
    }

    private AppointmentRequest request(LocalDateTime start) {
        return new AppointmentRequest(1, 1, 1, start,
                "Tooth pain", "Test appointment");
    }

    private LocalDateTime futureWorkingDay() {
        LocalDate date = LocalDate.now().plusDays(1);
        while (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.plusDays(1);
        }
        return date.atTime(10, 0);
    }

    private static class FakePatientDao implements PatientDao {
        private final Patient patient = new Patient(
                1, "PAT-TEST", "Nimal Perera", "Colombo",
                "0771234567", null, null, null, null, null,
                true, 1, LocalDateTime.now(), LocalDateTime.now());

        @Override public Patient create(Patient patient) { return patient; }
        @Override public Optional<Patient> findById(long id) {
            return id == 1 ? Optional.of(patient) : Optional.empty();
        }
        @Override public Optional<Patient> findByCode(String code) { return Optional.empty(); }
        @Override public List<Patient> search(String term, boolean inactive) {
            return List.of(patient);
        }
        @Override public boolean update(Patient patient) { return true; }
        @Override public boolean setActive(long id, boolean active) { return true; }
    }

    private static class FakeDentistDao implements DentistDao {
        private final Dentist dentist = new Dentist(
                1, "Dr. Perera", "SLDC-001", "General Dentistry",
                new BigDecimal("2500.00"), true);
        @Override public List<Dentist> findActive() { return List.of(dentist); }
        @Override public Optional<Dentist> findById(long id) {
            return id == 1 ? Optional.of(dentist) : Optional.empty();
        }
    }

    private static class FakeTreatmentDao implements TreatmentDao {
        private final Treatment treatment = new Treatment(
                1, "FILL", "Dental Filling", null,
                new BigDecimal("8000.00"), 60, true);
        @Override public List<Treatment> findActive() { return List.of(treatment); }
        @Override public Optional<Treatment> findById(long id) {
            return id == 1 ? Optional.of(treatment) : Optional.empty();
        }
    }

    private static class FakeAppointmentDao implements AppointmentDao {
        private final List<Appointment> appointments = new ArrayList<>();
        private boolean dentistConflict;
        private boolean patientConflict;

        @Override
        public Appointment create(Appointment source) {
            Appointment saved = copy(
                    source, appointments.size() + 1,
                    source.status(), 1);
            appointments.add(saved);
            return saved;
        }

        @Override public Optional<Appointment> findById(long id) {
            return appointments.stream()
                    .filter(item -> item.appointmentId() == id).findFirst();
        }
        @Override public Optional<Appointment> findByNumber(String number) {
            return appointments.stream()
                    .filter(item -> item.appointmentNumber().equals(number)).findFirst();
        }
        @Override public List<Appointment> search(
                String term, LocalDate date, AppointmentStatus status) {
            return List.copyOf(appointments);
        }
        @Override public boolean hasDentistConflict(
                long id, LocalDateTime start, LocalDateTime end, Long excluded) {
            return dentistConflict;
        }
        @Override public boolean hasPatientConflict(
                long id, LocalDateTime start, LocalDateTime end, Long excluded) {
            return patientConflict;
        }
        @Override public boolean updateSchedule(Appointment appointment, int version) {
            return true;
        }
        @Override public boolean updateStatus(
                long id, AppointmentStatus status, String reason, int version) {
            Optional<Appointment> current = findById(id);
            if (current.isEmpty() || current.get().versionNumber() != version) return false;
            appointments.remove(current.get());
            appointments.add(copy(current.get(), id, status, version + 1));
            return true;
        }

        private Appointment copy(
                Appointment source, long id,
                AppointmentStatus status, int version
        ) {
            return new Appointment(
                    id, source.appointmentNumber(), source.patientId(),
                    "PAT-TEST", "Nimal Perera", "0771234567",
                    source.dentistId(), "Dr. Perera",
                    source.treatmentTypeId(), "Dental Filling",
                    source.treatmentFee(), source.durationMinutes(),
                    source.consultationFee(), source.startAt(), source.endAt(),
                    status, source.patientReason(), source.internalNotes(),
                    source.cancellationReason(), source.createdBy(),
                    LocalDateTime.now(), LocalDateTime.now(), version);
        }
    }
}