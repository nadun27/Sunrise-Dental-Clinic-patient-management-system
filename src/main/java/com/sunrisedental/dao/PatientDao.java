package com.sunrisedental.dao;

import com.sunrisedental.model.Patient;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface PatientDao {

    Patient create(Patient patient) throws SQLException;

    Optional<Patient> findById(long patientId) throws SQLException;

    Optional<Patient> findByCode(String patientCode) throws SQLException;

    List<Patient> search(
            String searchTerm,
            boolean includeInactive
    ) throws SQLException;

    boolean update(Patient patient) throws SQLException;

    boolean setActive(
            long patientId,
            boolean active
    ) throws SQLException;
}