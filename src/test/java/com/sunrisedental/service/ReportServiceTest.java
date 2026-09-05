package com.sunrisedental.service;

import com.sunrisedental.dao.ReportDao;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.ClinicReport;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {

    @Test
    void generatesReportForValidDateRange()
            throws SQLException {

        FakeReportDao reportDao =
                new FakeReportDao();

        ReportService service =
                new ReportService(reportDao);

        LocalDate fromDate =
                LocalDate.of(2026, 8, 1);

        LocalDate toDate =
                LocalDate.of(2026, 8, 31);

        ClinicReport report =
                service.generate(
                        fromDate,
                        toDate
                );

        assertNotNull(report);
        assertEquals(12, report.totalAppointments());
        assertEquals(fromDate, reportDao.fromDate);
        assertEquals(toDate, reportDao.toDate);
    }

    @Test
    void rejectsMissingFromDate() {

        ReportService service =
                new ReportService(
                        new FakeReportDao()
                );

        ValidationException exception =
                assertThrows(
                        ValidationException.class,
                        () -> service.generate(
                                null,
                                LocalDate.now()
                        )
                );

        assertEquals(
                "Select both report dates",
                exception.getMessage()
        );
    }

    @Test
    void rejectsMissingToDate() {

        ReportService service =
                new ReportService(
                        new FakeReportDao()
                );

        assertThrows(
                ValidationException.class,
                () -> service.generate(
                        LocalDate.now(),
                        null
                )
        );
    }

    @Test
    void rejectsReversedDateRange() {

        ReportService service =
                new ReportService(
                        new FakeReportDao()
                );

        ValidationException exception =
                assertThrows(
                        ValidationException.class,
                        () -> service.generate(
                                LocalDate.of(
                                        2026,
                                        8,
                                        31
                                ),
                                LocalDate.of(
                                        2026,
                                        8,
                                        1
                                )
                        )
                );

        assertEquals(
                "From date cannot be after to date",
                exception.getMessage()
        );
    }

    @Test
    void rejectsPeriodLongerThan366Days() {

        ReportService service =
                new ReportService(
                        new FakeReportDao()
                );

        ValidationException exception =
                assertThrows(
                        ValidationException.class,
                        () -> service.generate(
                                LocalDate.of(
                                        2025,
                                        1,
                                        1
                                ),
                                LocalDate.of(
                                        2026,
                                        1,
                                        2
                                )
                        )
                );

        assertEquals(
                "Report period cannot exceed 366 days",
                exception.getMessage()
        );
    }

    private static class FakeReportDao
            implements ReportDao {

        private LocalDate fromDate;
        private LocalDate toDate;

        @Override
        public ClinicReport generate(
                LocalDate fromDate,
                LocalDate toDate
        ) {
            this.fromDate = fromDate;
            this.toDate = toDate;

            return new ClinicReport(
                    fromDate,
                    toDate,
                    12,
                    8,
                    1,
                    4,
                    7,
                    new BigDecimal("45000.00"),
                    new BigDecimal("35000.00"),
                    new BigDecimal("10000.00"),
                    List.of(
                            new ClinicReport
                                    .AppointmentStatusTotal(
                                    "COMPLETED",
                                    8
                            )
                    ),
                    List.of(
                            new ClinicReport.TreatmentTotal(
                                    "Dental Cleaning",
                                    5,
                                    new BigDecimal("25000.00")
                            )
                    ),
                    List.of(
                            new ClinicReport.DentistTotal(
                                    "Dr. Perera",
                                    12,
                                    8
                            )
                    )
            );
        }
    }
}