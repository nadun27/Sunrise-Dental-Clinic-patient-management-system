package com.sunrisedental.dao;

import com.sunrisedental.model.Treatment;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface TreatmentDao {

    List<Treatment> findActive()
            throws SQLException;

    Optional<Treatment> findById(long treatmentTypeId)
            throws SQLException;
}