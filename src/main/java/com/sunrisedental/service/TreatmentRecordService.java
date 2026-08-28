package com.sunrisedental.service;

import com.sunrisedental.dao.AppointmentDao;
import com.sunrisedental.dao.DentistDao;
import com.sunrisedental.dao.TreatmentRecordDao;
import com.sunrisedental.dto.request.TreatmentRecordRequest;
import com.sunrisedental.exception.NotFoundException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.Dentist;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.TreatmentRecord;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class TreatmentRecordService {

    private final TreatmentRecordDao treatmentRecordDao;
    private final AppointmentDao appointmentDao;
    private final DentistDao dentistDao;

    public TreatmentRecordService(
            TreatmentRecordDao treatmentRecordDao,
            AppointmentDao appointmentDao,
            DentistDao dentistDao
    ) {
        this.treatmentRecordDao = treatmentRecordDao;
        this.appointmentDao = appointmentDao;
        this.dentistDao = dentistDao;
    }

    public Appointment startSession(
            long appointmentId,
            int expectedVersion,
            long userId,
            Role role
    ) throws SQLException {

        validatePositiveId(appointmentId, "Appointment");
        validateVersion(expectedVersion);

        Appointment appointment =
                getAppointment(appointmentId);

        authorizeClinicalAction(
                appointment,
                userId,
                role
        );

        if (appointment.status() !=
                AppointmentStatus.CHECKED_IN) {

            throw new ValidationException(
                    "Only a checked-in appointment " +
                            "can start treatment"
            );
        }

        boolean updated = appointmentDao.updateStatus(
                appointmentId,
                AppointmentStatus.IN_TREATMENT,
                null,
                expectedVersion
        );

        if (!updated) {
            throw concurrentUpdateException();
        }

        return getAppointment(appointmentId);
    }

    public TreatmentRecord completeSession(
            TreatmentRecordRequest request,
            long userId,
            Role role
    ) throws SQLException {

        if (request == null) {
            throw new ValidationException(
                    "Treatment record information is required"
            );
        }

        validatePositiveId(
                request.appointmentId(),
                "Appointment"
        );

        validateVersion(request.versionNumber());

        Appointment appointment =
                getAppointment(request.appointmentId());

        authorizeClinicalAction(
                appointment,
                userId,
                role
        );

        if (appointment.status() !=
                AppointmentStatus.IN_TREATMENT) {

            throw new ValidationException(
                    "Only an appointment in treatment " +
                            "can be completed"
            );
        }

        if (treatmentRecordDao.findByAppointmentId(
                appointment.appointmentId()
        ).isPresent()) {

            throw new ValidationException(
                    "A treatment record already exists " +
                            "for this appointment"
            );
        }

        LocalDate followUpDate =
                validateFollowUpDate(
                        request.followUpDate()
                );

        TreatmentRecord treatmentRecord =
                new TreatmentRecord(
                        0,
                        appointment.appointmentId(),
                        null,
                        null,
                        null,
                        appointment.dentistId(),
                        null,
                        null,
                        requireText(
                                request.diagnosis(),
                                1000,
                                "Diagnosis"
                        ),
                        requireText(
                                request.treatmentPerformed(),
                                1000,
                                "Treatment performed"
                        ),
                        normalizeOptional(
                                request.clinicalNotes(),
                                10000,
                                "Clinical notes"
                        ),
                        normalizeOptional(
                                request.prescription(),
                                10000,
                                "Prescription"
                        ),
                        followUpDate,
                        null,
                        null
                );

        return treatmentRecordDao.completeSession(
                treatmentRecord,
                request.versionNumber()
        );
    }

    public TreatmentRecord getById(
            long treatmentRecordId,
            long userId,
            Role role
    ) throws SQLException {

        validatePositiveId(
                treatmentRecordId,
                "Treatment record"
        );

        TreatmentRecord treatmentRecord =
                treatmentRecordDao.findById(
                treatmentRecordId
        ).orElseThrow(() ->
                new NotFoundException(
                        "Treatment record was not found"
                )
        );

        authorizeRecordAccess(
                treatmentRecord,
                userId,
                role
        );

        return treatmentRecord;
    }

    public TreatmentRecord getByAppointmentId(
            long appointmentId,
            long userId,
            Role role
    ) throws SQLException {

        validatePositiveId(appointmentId, "Appointment");

        TreatmentRecord treatmentRecord =
                treatmentRecordDao.findByAppointmentId(
                appointmentId
        ).orElseThrow(() ->
                new NotFoundException(
                        "Treatment record was not found"
                )
        );

        authorizeRecordAccess(
                treatmentRecord,
                userId,
                role
        );

        return treatmentRecord;
    }

    public List<TreatmentRecord> search(
            String searchTerm,
            long userId,
            Role role
    ) throws SQLException {

        String normalizedTerm = searchTerm == null
                ? ""
                : searchTerm.trim();

        if (normalizedTerm.length() > 100) {
            throw new ValidationException(
                    "Search text cannot exceed 100 characters"
            );
        }

        List<TreatmentRecord> records =
                treatmentRecordDao.search(normalizedTerm);

        if (role == Role.ADMIN) {
            return records;
        }

        Dentist dentist = requireDentist(userId, role);

        return records.stream()
                .filter(record ->
                        record.dentistId() ==
                                dentist.dentistId()
                )
                .toList();
    }

    public List<Appointment> getActiveSessions(
            long userId,
            Role role
    ) throws SQLException {

        List<Appointment> activeSessions =
                new java.util.ArrayList<>();

        activeSessions.addAll(
                appointmentDao.search(
                        "",
                        null,
                        AppointmentStatus.CHECKED_IN
                )
        );

        activeSessions.addAll(
                appointmentDao.search(
                        "",
                        null,
                        AppointmentStatus.IN_TREATMENT
                )
        );

        if (role == Role.ADMIN) {
            return activeSessions;
        }

        Dentist dentist = requireDentist(userId, role);

        return activeSessions.stream()
                .filter(appointment ->
                        appointment.dentistId() ==
                                dentist.dentistId()
                )
                .sorted(java.util.Comparator.comparing(
                        Appointment::startAt
                ))
                .toList();
    }

    private Appointment getAppointment(
            long appointmentId
    ) throws SQLException {

        return appointmentDao.findById(appointmentId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Appointment was not found"
                        )
                );
    }

    private void authorizeClinicalAction(
            Appointment appointment,
            long userId,
            Role role
    ) throws SQLException {

        validatePositiveId(userId, "Staff user");

        if (role == Role.ADMIN) {
            return;
        }

        Dentist dentist = requireDentist(userId, role);

        if (dentist.dentistId() !=
                appointment.dentistId()) {

            throw new ValidationException(
                    "Only the assigned dentist can manage " +
                            "this treatment session"
            );
        }
    }

    private void authorizeRecordAccess(
            TreatmentRecord treatmentRecord,
            long userId,
            Role role
    ) throws SQLException {

        if (role == Role.ADMIN) {
            return;
        }

        Dentist dentist = requireDentist(userId, role);

        if (dentist.dentistId() !=
                treatmentRecord.dentistId()) {

            throw new ValidationException(
                    "You do not have permission to view " +
                            "this treatment record"
            );
        }
    }

    private Dentist requireDentist(
            long userId,
            Role role
    ) throws SQLException {

        validatePositiveId(userId, "Staff user");

        if (role != Role.DENTIST) {
            throw new ValidationException(
                    "Only an administrator or dentist " +
                            "can manage treatment sessions"
            );
        }

        Dentist dentist = dentistDao.findByUserId(userId)
                .orElseThrow(() ->
                        new ValidationException(
                                "A dentist profile is not linked " +
                                        "to this account"
                        )
                );

        if (!dentist.active()) {
            throw new ValidationException(
                    "The dentist profile is inactive"
            );
        }

        return dentist;
    }

    private String requireText(
            String value,
            int maximumLength,
            String fieldName
    ) {

        String normalized = normalizeOptional(
                value,
                maximumLength,
                fieldName
        );

        if (normalized == null) {
            throw new ValidationException(
                    fieldName + " is required"
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

    private LocalDate validateFollowUpDate(
            LocalDate followUpDate
    ) {

        if (followUpDate != null &&
                followUpDate.isBefore(LocalDate.now())) {

            throw new ValidationException(
                    "Follow-up date cannot be in the past"
            );
        }

        return followUpDate;
    }

    private void validatePositiveId(
            long value,
            String fieldName
    ) {

        if (value <= 0) {
            throw new ValidationException(
                    fieldName + " must be valid"
            );
        }
    }

    private void validateVersion(int versionNumber) {
        if (versionNumber <= 0) {
            throw new ValidationException(
                    "Appointment version is invalid"
            );
        }
    }

    private ValidationException concurrentUpdateException() {
        return new ValidationException(
                "The appointment was changed by another user. " +
                        "Refresh and try again"
        );
    }
}
