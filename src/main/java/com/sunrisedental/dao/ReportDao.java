package com.sunrisedental.dao;

import com.sunrisedental.model.ClinicReport;

import java.sql.SQLException;
import java.time.LocalDate;

public interface ReportDao {

    ClinicReport generate(
            LocalDate fromDate,
            LocalDate toDate
    ) throws SQLException;
}