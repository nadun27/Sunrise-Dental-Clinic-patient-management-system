package com.sunrisedental.service;

import com.sunrisedental.dao.ReportDao;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.ClinicReport;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ReportService {

    private static final long MAXIMUM_REPORT_DAYS = 366;

    private final ReportDao reportDao;

    public ReportService(ReportDao reportDao) {
        this.reportDao = reportDao;
    }

    public ClinicReport generate(
            LocalDate fromDate,
            LocalDate toDate
    ) throws SQLException {

        validateDateRange(fromDate, toDate);

        return reportDao.generate(
                fromDate,
                toDate
        );
    }

    private void validateDateRange(
            LocalDate fromDate,
            LocalDate toDate
    ) {
        if (fromDate == null || toDate == null) {
            throw new ValidationException(
                    "Select both report dates"
            );
        }

        if (fromDate.isAfter(toDate)) {
            throw new ValidationException(
                    "From date cannot be after to date"
            );
        }

        long inclusiveDays =
                ChronoUnit.DAYS.between(
                        fromDate,
                        toDate
                ) + 1;

        if (inclusiveDays > MAXIMUM_REPORT_DAYS) {
            throw new ValidationException(
                    "Report period cannot exceed 366 days"
            );
        }
    }
}