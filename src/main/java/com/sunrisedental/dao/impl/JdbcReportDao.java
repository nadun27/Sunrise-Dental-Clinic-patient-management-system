package com.sunrisedental.dao.impl;

import com.sunrisedental.config.DatabaseConfig;
import com.sunrisedental.dao.ReportDao;
import com.sunrisedental.model.ClinicReport;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class JdbcReportDao implements ReportDao {

    @Override
    public ClinicReport generate(
            LocalDate fromDate,
            LocalDate toDate
    ) throws SQLException {

        Timestamp rangeStart =
                Timestamp.valueOf(
                        fromDate.atStartOfDay()
                );

        Timestamp rangeEnd =
                Timestamp.valueOf(
                        toDate.plusDays(1).atStartOfDay()
                );

        try (Connection connection =
                     DatabaseConfig.getConnection()) {

            AppointmentStats appointmentStats =
                    loadAppointmentStats(
                            connection,
                            rangeStart,
                            rangeEnd
                    );

            long newPatients =
                    loadNewPatientCount(
                            connection,
                            rangeStart,
                            rangeEnd
                    );

            RevenueStats revenueStats =
                    loadRevenueStats(
                            connection,
                            rangeStart,
                            rangeEnd
                    );

            List<ClinicReport.TreatmentTotal>
                    treatmentTotals =
                    loadTreatmentTotals(
                            connection,
                            rangeStart,
                            rangeEnd
                    );

            List<ClinicReport.DentistTotal>
                    dentistTotals =
                    loadDentistTotals(
                            connection,
                            rangeStart,
                            rangeEnd
                    );

            return new ClinicReport(
                    fromDate,
                    toDate,
                    appointmentStats.total(),
                    appointmentStats.completed(),
                    appointmentStats.cancelled(),
                    newPatients,
                    revenueStats.billCount(),
                    revenueStats.billedAmount(),
                    revenueStats.collectedAmount(),
                    revenueStats.outstandingAmount(),
                    appointmentStats.statusTotals(),
                    treatmentTotals,
                    dentistTotals
            );
        }
    }

    private AppointmentStats loadAppointmentStats(
            Connection connection,
            Timestamp rangeStart,
            Timestamp rangeEnd
    ) throws SQLException {

        String sql = """
                SELECT status,
                       COUNT(*) AS appointment_count
                FROM appointments
                WHERE start_at >= ?
                  AND start_at < ?
                GROUP BY status
                ORDER BY status
                """;

        long total = 0;
        long completed = 0;
        long cancelled = 0;

        List<ClinicReport.AppointmentStatusTotal>
                statusTotals = new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            setRange(
                    statement,
                    rangeStart,
                    rangeEnd
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    String status =
                            resultSet.getString("status");

                    long count =
                            resultSet.getLong(
                                    "appointment_count"
                            );

                    total += count;

                    if ("COMPLETED".equals(status)) {
                        completed = count;
                    }

                    if ("CANCELLED".equals(status)) {
                        cancelled = count;
                    }

                    statusTotals.add(
                            new ClinicReport
                                    .AppointmentStatusTotal(
                                    status,
                                    count
                            )
                    );
                }
            }
        }

        return new AppointmentStats(
                total,
                completed,
                cancelled,
                statusTotals
        );
    }

    private long loadNewPatientCount(
            Connection connection,
            Timestamp rangeStart,
            Timestamp rangeEnd
    ) throws SQLException {

        String sql = """
                SELECT COUNT(*) AS patient_count
                FROM patients
                WHERE created_at >= ?
                  AND created_at < ?
                """;

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            setRange(
                    statement,
                    rangeStart,
                    rangeEnd
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                resultSet.next();

                return resultSet.getLong(
                        "patient_count"
                );
            }
        }
    }

    private RevenueStats loadRevenueStats(
            Connection connection,
            Timestamp rangeStart,
            Timestamp rangeEnd
    ) throws SQLException {

        String billSql = """
                SELECT COUNT(*) AS bill_count,
                       COALESCE(
                           SUM(b.total_amount),
                           0
                       ) AS billed_amount,
                       COALESCE(
                           SUM(
                               GREATEST(
                                   b.total_amount -
                                   (
                                       SELECT COALESCE(
                                           SUM(p.amount),
                                           0
                                       )
                                       FROM payments p
                                       WHERE p.bill_id = b.bill_id
                                         AND p.voided_at IS NULL
                                   ),
                                   0
                               )
                           ),
                           0
                       ) AS outstanding_amount
                FROM bills b
                WHERE b.created_at >= ?
                  AND b.created_at < ?
                  AND b.payment_status <> 'VOID'
                """;

        long billCount;
        BigDecimal billedAmount;
        BigDecimal outstandingAmount;

        try (PreparedStatement statement =
                     connection.prepareStatement(billSql)) {

            setRange(
                    statement,
                    rangeStart,
                    rangeEnd
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                resultSet.next();

                billCount =
                        resultSet.getLong("bill_count");

                billedAmount =
                        resultSet.getBigDecimal(
                                "billed_amount"
                        );

                outstandingAmount =
                        resultSet.getBigDecimal(
                                "outstanding_amount"
                        );
            }
        }

        String paymentSql = """
                SELECT COALESCE(
                           SUM(amount),
                           0
                       ) AS collected_amount
                FROM payments
                WHERE paid_at >= ?
                  AND paid_at < ?
                  AND voided_at IS NULL
                """;

        BigDecimal collectedAmount;

        try (PreparedStatement statement =
                     connection.prepareStatement(paymentSql)) {

            setRange(
                    statement,
                    rangeStart,
                    rangeEnd
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                resultSet.next();

                collectedAmount =
                        resultSet.getBigDecimal(
                                "collected_amount"
                        );
            }
        }

        return new RevenueStats(
                billCount,
                billedAmount,
                collectedAmount,
                outstandingAmount
        );
    }

    private List<ClinicReport.TreatmentTotal>
    loadTreatmentTotals(
            Connection connection,
            Timestamp rangeStart,
            Timestamp rangeEnd
    ) throws SQLException {

        String sql = """
                SELECT tt.treatment_name,
                       COUNT(a.appointment_id)
                           AS appointment_count,
                       COALESCE(
                           SUM(
                               CASE
                                   WHEN b.payment_status IS NULL
                                        OR b.payment_status = 'VOID'
                                   THEN 0
                                   ELSE b.total_amount
                               END
                           ),
                           0
                       ) AS billed_amount
                FROM appointments a
                INNER JOIN treatment_types tt
                        ON tt.treatment_type_id =
                           a.treatment_type_id
                LEFT JOIN bills b
                       ON b.appointment_id =
                          a.appointment_id
                WHERE a.start_at >= ?
                  AND a.start_at < ?
                GROUP BY
                    tt.treatment_type_id,
                    tt.treatment_name
                ORDER BY
                    appointment_count DESC,
                    tt.treatment_name
                """;

        List<ClinicReport.TreatmentTotal> totals =
                new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            setRange(
                    statement,
                    rangeStart,
                    rangeEnd
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    totals.add(
                            new ClinicReport.TreatmentTotal(
                                    resultSet.getString(
                                            "treatment_name"
                                    ),
                                    resultSet.getLong(
                                            "appointment_count"
                                    ),
                                    resultSet.getBigDecimal(
                                            "billed_amount"
                                    )
                            )
                    );
                }
            }
        }

        return totals;
    }

    private List<ClinicReport.DentistTotal>
    loadDentistTotals(
            Connection connection,
            Timestamp rangeStart,
            Timestamp rangeEnd
    ) throws SQLException {

        String sql = """
                SELECT u.full_name AS dentist_name,
                       COUNT(a.appointment_id)
                           AS appointment_count,
                       SUM(
                           CASE
                               WHEN a.status = 'COMPLETED'
                               THEN 1
                               ELSE 0
                           END
                       ) AS completed_count
                FROM appointments a
                INNER JOIN dentists d
                        ON d.dentist_id = a.dentist_id
                INNER JOIN users u
                        ON u.user_id = d.user_id
                WHERE a.start_at >= ?
                  AND a.start_at < ?
                GROUP BY
                    d.dentist_id,
                    u.full_name
                ORDER BY
                    appointment_count DESC,
                    u.full_name
                """;

        List<ClinicReport.DentistTotal> totals =
                new ArrayList<>();

        try (PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            setRange(
                    statement,
                    rangeStart,
                    rangeEnd
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    totals.add(
                            new ClinicReport.DentistTotal(
                                    resultSet.getString(
                                            "dentist_name"
                                    ),
                                    resultSet.getLong(
                                            "appointment_count"
                                    ),
                                    resultSet.getLong(
                                            "completed_count"
                                    )
                            )
                    );
                }
            }
        }

        return totals;
    }

    private void setRange(
            PreparedStatement statement,
            Timestamp rangeStart,
            Timestamp rangeEnd
    ) throws SQLException {

        statement.setTimestamp(1, rangeStart);
        statement.setTimestamp(2, rangeEnd);
    }

    private record AppointmentStats(
            long total,
            long completed,
            long cancelled,
            List<ClinicReport.AppointmentStatusTotal>
            statusTotals
    ) {
    }

    private record RevenueStats(
            long billCount,
            BigDecimal billedAmount,
            BigDecimal collectedAmount,
            BigDecimal outstandingAmount
    ) {
    }
}