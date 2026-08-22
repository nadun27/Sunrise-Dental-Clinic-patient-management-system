package com.sunrisedental.service;

import com.sunrisedental.dao.AppointmentDao;
import com.sunrisedental.dao.DentistDao;
import com.sunrisedental.dao.PatientDao;
import com.sunrisedental.dao.TreatmentDao;
import com.sunrisedental.dto.request.AppointmentRequest;
import com.sunrisedental.exception.NotFoundException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.Dentist;
import com.sunrisedental.model.Patient;
import com.sunrisedental.model.Treatment;
import com.sunrisedental.util.AppointmentNumberGenerator;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppointmentService {

    private static final LocalTime OPENING_TIME =
            LocalTime.of(8, 0);

    private static final LocalTime CLOSING_TIME =
            LocalTime.of(18, 0);

    private static final Map<
            AppointmentStatus,
            Set<AppointmentStatus>
            > STATUS_TRANSITIONS = Map.of(

            AppointmentStatus.SCHEDULED,
            Set.of(
                    AppointmentStatus.CONFIRMED,
                    AppointmentStatus.CANCELLED
            ),

            AppointmentStatus.CONFIRMED,
            Set.of(
                    AppointmentStatus.CHECKED_IN,
                    AppointmentStatus.CANCELLED,
                    AppointmentStatus.NO_SHOW
            ),

            AppointmentStatus.CHECKED_IN,
            Set.of(
                    AppointmentStatus.IN_TREATMENT,
                    AppointmentStatus.CANCELLED
            ),

            AppointmentStatus.IN_TREATMENT,
            Set.of(AppointmentStatus.COMPLETED),

            AppointmentStatus.COMPLETED,
            Set.of(),

            AppointmentStatus.CANCELLED,
            Set.of(),

            AppointmentStatus.NO_SHOW,
            Set.of()
    );

    private final AppointmentDao appointmentDao;
    private final PatientDao patientDao;
    private final DentistDao dentistDao;
    private final TreatmentDao treatmentDao;

    public AppointmentService(
            AppointmentDao appointmentDao,
            PatientDao patientDao,
            DentistDao dentistDao,
            TreatmentDao treatmentDao
    ) {
        this.appointmentDao = appointmentDao;
        this.patientDao = patientDao;
        this.dentistDao = dentistDao;
        this.treatmentDao = treatmentDao;
    }

    public Appointment create(
            AppointmentRequest request,
            long createdBy
    ) throws SQLException {

        if (createdBy <= 0) {
            throw new ValidationException(
                    "Valid staff user is required"
            );
        }

        ValidatedSchedule schedule =
                validateSchedule(request, null);

        String appointmentNumber =
                generateUniqueNumber();

        Appointment appointment = new Appointment(
                0,
                appointmentNumber,

                schedule.patient().patientId(),
                null,
                null,
                null,

                schedule.dentist().dentistId(),
                null,

                schedule.treatment().treatmentTypeId(),
                null,
                schedule.treatment().defaultFee(),
                schedule.treatment()
                        .defaultDurationMinutes(),

                schedule.dentist().consultationFee(),

                schedule.startAt(),
                schedule.endAt(),
                AppointmentStatus.SCHEDULED,

                normalizeOptional(
                        request.patientReason(),
                        500,
                        "Patient reason"
                ),

                normalizeOptional(
                        request.internalNotes(),
                        500,
                        "Internal notes"
                ),

                null,
                createdBy,
                null,
                null,
                1
        );

        return appointmentDao.create(appointment);
    }

    public Appointment getById(long appointmentId)
            throws SQLException {

        validateAppointmentId(appointmentId);

        return appointmentDao.findById(appointmentId)
                .orElseThrow(() -> new NotFoundException(
                        "Appointment was not found"
                ));
    }

    public Appointment getByNumber(
            String appointmentNumber
    ) throws SQLException {

        if (appointmentNumber == null ||
                appointmentNumber.isBlank()) {

            throw new ValidationException(
                    "Appointment number is required"
            );
        }

        return appointmentDao.findByNumber(
                        appointmentNumber
                                .trim()
                                .toUpperCase()
                )
                .orElseThrow(() -> new NotFoundException(
                        "Appointment was not found"
                ));
    }

    public List<Appointment> search(
            String searchTerm,
            LocalDate date,
            String statusValue
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

        AppointmentStatus status = null;

        if (statusValue != null &&
                !statusValue.isBlank()) {

            try {
                status = AppointmentStatus.valueOf(
                        statusValue
                                .trim()
                                .toUpperCase()
                );

            } catch (IllegalArgumentException exception) {
                throw new ValidationException(
                        "Select a valid appointment status"
                );
            }
        }

        return appointmentDao.search(
                normalizedTerm,
                date,
                status
        );
    }

    public Appointment reschedule(
            long appointmentId,
            AppointmentRequest request,
            int expectedVersion
    ) throws SQLException {

        Appointment existingAppointment =
                getById(appointmentId);

        if (existingAppointment.status() !=
                AppointmentStatus.SCHEDULED &&

                existingAppointment.status() !=
                        AppointmentStatus.CONFIRMED) {

            throw new ValidationException(
                    "Only scheduled or confirmed " +
                            "appointments can be rescheduled"
            );
        }

        validateVersion(expectedVersion);

        ValidatedSchedule schedule =
                validateSchedule(
                        request,
                        appointmentId
                );

        Appointment updatedAppointment =
                new Appointment(
                        existingAppointment.appointmentId(),
                        existingAppointment
                                .appointmentNumber(),

                        schedule.patient().patientId(),
                        null,
                        null,
                        null,

                        schedule.dentist().dentistId(),
                        null,

                        schedule.treatment()
                                .treatmentTypeId(),

                        null,
                        schedule.treatment().defaultFee(),

                        schedule.treatment()
                                .defaultDurationMinutes(),

                        schedule.dentist()
                                .consultationFee(),

                        schedule.startAt(),
                        schedule.endAt(),
                        existingAppointment.status(),

                        normalizeOptional(
                                request.patientReason(),
                                500,
                                "Patient reason"
                        ),

                        normalizeOptional(
                                request.internalNotes(),
                                500,
                                "Internal notes"
                        ),

                        existingAppointment
                                .cancellationReason(),

                        existingAppointment.createdBy(),
                        existingAppointment.createdAt(),
                        existingAppointment.updatedAt(),
                        existingAppointment.versionNumber()
                );

        boolean updated =
                appointmentDao.updateSchedule(
                        updatedAppointment,
                        expectedVersion
                );

        if (!updated) {
            throw new ValidationException(
                    "The appointment was changed by " +
                            "another user. Refresh and try again"
            );
        }

        return getById(appointmentId);
    }

    public Appointment changeStatus(
            long appointmentId,
            String statusValue,
            String cancellationReason,
            int expectedVersion
    ) throws SQLException {

        Appointment existingAppointment =
                getById(appointmentId);

        validateVersion(expectedVersion);

        AppointmentStatus newStatus;

        try {
            newStatus = AppointmentStatus.valueOf(
                    statusValue == null
                            ? ""
                            : statusValue
                            .trim()
                            .toUpperCase()
            );

        } catch (IllegalArgumentException exception) {
            throw new ValidationException(
                    "Select a valid appointment status"
            );
        }

        Set<AppointmentStatus> allowedStatuses =
                STATUS_TRANSITIONS.get(
                        existingAppointment.status()
                );

        if (!allowedStatuses.contains(newStatus)) {
            throw new ValidationException(
                    "Status cannot change from " +
                            existingAppointment.status() +
                            " to " + newStatus
            );
        }

        String normalizedReason =
                normalizeOptional(
                        cancellationReason,
                        255,
                        "Cancellation reason"
                );

        if (newStatus ==
                AppointmentStatus.CANCELLED &&

                normalizedReason == null) {

            throw new ValidationException(
                    "Cancellation reason is required"
            );
        }

        if (newStatus !=
                AppointmentStatus.CANCELLED) {

            normalizedReason = null;
        }

        boolean updated =
                appointmentDao.updateStatus(
                        appointmentId,
                        newStatus,
                        normalizedReason,
                        expectedVersion
                );

        if (!updated) {
            throw new ValidationException(
                    "The appointment was changed by " +
                            "another user. Refresh and try again"
            );
        }

        return getById(appointmentId);
    }

    public List<Dentist> getActiveDentists()
            throws SQLException {

        return dentistDao.findActive();
    }

    public List<Treatment> getActiveTreatments()
            throws SQLException {

        return treatmentDao.findActive();
    }

    private ValidatedSchedule validateSchedule(
            AppointmentRequest request,
            Long excludedAppointmentId
    ) throws SQLException {

        if (request == null) {
            throw new ValidationException(
                    "Appointment information is required"
            );
        }

        Patient patient =
                patientDao.findById(request.patientId())
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Patient was not found"
                                )
                        );

        if (!patient.active()) {
            throw new ValidationException(
                    "Inactive patients cannot receive appointments"
            );
        }

        Dentist dentist =
                dentistDao.findById(request.dentistId())
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Dentist was not found"
                                )
                        );

        if (!dentist.active()) {
            throw new ValidationException(
                    "Selected dentist is inactive"
            );
        }

        Treatment treatment =
                treatmentDao.findById(
                                request.treatmentTypeId()
                        )
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Treatment was not found"
                                )
                        );

        if (!treatment.active()) {
            throw new ValidationException(
                    "Selected treatment is inactive"
            );
        }

        LocalDateTime startAt = request.startAt();

        if (startAt == null) {
            throw new ValidationException(
                    "Appointment date and time are required"
            );
        }

        if (!startAt.isAfter(LocalDateTime.now())) {
            throw new ValidationException(
                    "Appointment must be scheduled in the future"
            );
        }

        if (startAt.getDayOfWeek() ==
                DayOfWeek.SUNDAY) {

            throw new ValidationException(
                    "The clinic is closed on Sundays"
            );
        }

        if (startAt.getSecond() != 0 ||
                startAt.getNano() != 0 ||
                startAt.getMinute() % 15 != 0) {

            throw new ValidationException(
                    "Appointment time must use a " +
                            "15-minute interval"
            );
        }

        LocalDateTime endAt =
                startAt.plusMinutes(
                        treatment.defaultDurationMinutes()
                );

        boolean startsBeforeOpening =
                startAt.toLocalTime()
                        .isBefore(OPENING_TIME);

        boolean finishesAfterClosing =
                endAt.toLocalTime()
                        .isAfter(CLOSING_TIME);

        boolean finishesOnAnotherDay =
                !endAt.toLocalDate()
                        .equals(startAt.toLocalDate());

        if (startsBeforeOpening ||
                finishesAfterClosing ||
                finishesOnAnotherDay) {

            throw new ValidationException(
                    "Appointment must fit within clinic " +
                            "hours, 08:00 to 18:00"
            );
        }

        boolean dentistConflict =
                appointmentDao.hasDentistConflict(
                        dentist.dentistId(),
                        startAt,
                        endAt,
                        excludedAppointmentId
                );

        if (dentistConflict) {
            throw new ValidationException(
                    "The dentist already has an " +
                            "appointment during this time"
            );
        }

        boolean patientConflict =
                appointmentDao.hasPatientConflict(
                        patient.patientId(),
                        startAt,
                        endAt,
                        excludedAppointmentId
                );

        if (patientConflict) {
            throw new ValidationException(
                    "The patient already has an " +
                            "appointment during this time"
            );
        }

        return new ValidatedSchedule(
                patient,
                dentist,
                treatment,
                startAt,
                endAt
        );
    }

    private String generateUniqueNumber()
            throws SQLException {

        for (int attempt = 0;
             attempt < 5;
             attempt++) {

            String appointmentNumber =
                    AppointmentNumberGenerator.generate();

            if (appointmentDao.findByNumber(
                    appointmentNumber
            ).isEmpty()) {

                return appointmentNumber;
            }
        }

        throw new SQLException(
                "A unique appointment number " +
                        "could not be generated"
        );
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

    private void validateAppointmentId(
            long appointmentId
    ) {
        if (appointmentId <= 0) {
            throw new ValidationException(
                    "Invalid appointment ID"
            );
        }
    }

    private void validateVersion(int version) {
        if (version <= 0) {
            throw new ValidationException(
                    "Invalid appointment version"
            );
        }
    }

    private record ValidatedSchedule(
            Patient patient,
            Dentist dentist,
            Treatment treatment,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }
}