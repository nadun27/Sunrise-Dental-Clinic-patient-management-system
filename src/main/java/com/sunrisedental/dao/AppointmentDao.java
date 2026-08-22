package com.sunrisedental.dao;

import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AppointmentDao {

    Appointment create(Appointment appointment)
            throws SQLException;

    Optional<Appointment> findById(long appointmentId)
            throws SQLException;

    Optional<Appointment> findByNumber(
            String appointmentNumber
    ) throws SQLException;

    List<Appointment> search(
            String searchTerm,
            LocalDate date,
            AppointmentStatus status
    ) throws SQLException;

    boolean hasDentistConflict(
            long dentistId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Long excludedAppointmentId
    ) throws SQLException;

    boolean hasPatientConflict(
            long patientId,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Long excludedAppointmentId
    ) throws SQLException;

    boolean updateSchedule(
            Appointment appointment,
            int expectedVersion
    ) throws SQLException;

    boolean updateStatus(
            long appointmentId,
            AppointmentStatus status,
            String cancellationReason,
            int expectedVersion
    ) throws SQLException;
}