package com.sunrisedental.dao;

import com.sunrisedental.model.Dentist;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface DentistDao {

    List<Dentist> findActive()
            throws SQLException;

    Optional<Dentist> findById(long dentistId)
            throws SQLException;
}