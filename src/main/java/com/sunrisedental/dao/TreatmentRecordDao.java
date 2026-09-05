package com.sunrisedental.dao;

import com.sunrisedental.model.TreatmentRecord;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface TreatmentRecordDao {

    TreatmentRecord completeSession(
            TreatmentRecord treatmentRecord,
            int expectedVersion
    ) throws SQLException;

    Optional<TreatmentRecord> findById(
            long treatmentRecordId
    ) throws SQLException;

    Optional<TreatmentRecord> findByAppointmentId(
            long appointmentId
    ) throws SQLException;

    List<TreatmentRecord> search(
            String searchTerm
    ) throws SQLException;
}
