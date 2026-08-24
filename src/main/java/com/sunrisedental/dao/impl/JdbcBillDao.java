package com.sunrisedental.dao.impl;

import com.sunrisedental.config.DatabaseConfig;
import com.sunrisedental.dao.BillDao;
import com.sunrisedental.model.Bill;
import com.sunrisedental.model.PaymentStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcBillDao implements BillDao {

    private static final String SELECT_DETAILS = """
            SELECT b.bill_id,
                   b.invoice_number,
                   b.appointment_id,
                   a.appointment_number,
                   p.full_name AS patient_name,
                   b.consultation_fee,
                   b.treatment_fee,
                   b.discount_amount,
                   b.tax_amount,
                   b.total_amount,
                   b.payment_status,
                   b.discount_reason,
                   b.created_by,
                   b.created_at,
                   b.updated_at
            FROM bills b
            INNER JOIN appointments a
                    ON a.appointment_id = b.appointment_id
            INNER JOIN patients p
                    ON p.patient_id = a.patient_id
            """;

    @Override
    public Bill create(Bill bill)
            throws SQLException {

        String sql = """
                INSERT INTO bills (
                    invoice_number,
                    appointment_id,
                    consultation_fee,
                    treatment_fee,
                    discount_amount,
                    tax_amount,
                    total_amount,
                    payment_status,
                    discount_reason,
                    created_by
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        long billId;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(
                                sql,
                                Statement.RETURN_GENERATED_KEYS
                        )
        ) {
            statement.setString(
                    1,
                    bill.invoiceNumber()
            );

            statement.setLong(
                    2,
                    bill.appointmentId()
            );

            statement.setBigDecimal(
                    3,
                    bill.consultationFee()
            );

            statement.setBigDecimal(
                    4,
                    bill.treatmentFee()
            );

            statement.setBigDecimal(
                    5,
                    bill.discountAmount()
            );

            statement.setBigDecimal(
                    6,
                    bill.taxAmount()
            );

            statement.setBigDecimal(
                    7,
                    bill.totalAmount()
            );

            statement.setString(
                    8,
                    bill.paymentStatus().name()
            );

            setNullableString(
                    statement,
                    9,
                    bill.discountReason()
            );

            statement.setLong(
                    10,
                    bill.createdBy()
            );

            statement.executeUpdate();

            try (ResultSet keys =
                         statement.getGeneratedKeys()) {

                if (!keys.next()) {
                    throw new SQLException(
                            "Bill ID was not generated"
                    );
                }

                billId = keys.getLong(1);
            }
        }

        return findById(billId)
                .orElseThrow(() ->
                        new SQLException(
                                "Created bill could not be retrieved"
                        )
                );
    }

    @Override
    public Optional<Bill> findById(
            long billId
    ) throws SQLException {

        String sql =
                SELECT_DETAILS +
                        " WHERE b.bill_id = ? LIMIT 1";

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, billId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Bill> findByAppointmentId(
            long appointmentId
    ) throws SQLException {

        String sql =
                SELECT_DETAILS +
                        " WHERE b.appointment_id = ? LIMIT 1";

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, appointmentId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Bill> findByInvoiceNumber(
            String invoiceNumber
    ) throws SQLException {

        String sql =
                SELECT_DETAILS +
                        " WHERE b.invoice_number = ? LIMIT 1";

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(
                    1,
                    invoiceNumber
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        }
    }

    @Override
    public List<Bill> search(
            String searchTerm,
            PaymentStatus paymentStatus
    ) throws SQLException {

        String sql = SELECT_DETAILS + """
                WHERE (
                    ? = ''
                    OR LOWER(b.invoice_number) LIKE ?
                    OR LOWER(a.appointment_number) LIKE ?
                    OR LOWER(p.full_name) LIKE ?
                    OR p.contact_number LIKE ?
                )
                AND (
                    ? = ''
                    OR b.payment_status = ?
                )
                ORDER BY b.created_at DESC
                LIMIT 200
                """;

        String term =
                searchTerm == null
                        ? ""
                        : searchTerm
                        .trim()
                        .toLowerCase();

        String pattern = "%" + term + "%";

        String statusValue =
                paymentStatus == null
                        ? ""
                        : paymentStatus.name();

        List<Bill> bills = new ArrayList<>();

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, term);
            statement.setString(2, pattern);
            statement.setString(3, pattern);
            statement.setString(4, pattern);
            statement.setString(5, pattern);
            statement.setString(6, statusValue);
            statement.setString(7, statusValue);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    bills.add(map(resultSet));
                }
            }
        }

        return bills;
    }

    @Override
    public boolean invoiceNumberExists(
            String invoiceNumber
    ) throws SQLException {

        String sql = """
                SELECT 1
                FROM bills
                WHERE invoice_number = ?
                LIMIT 1
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(
                    1,
                    invoiceNumber
            );

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next();
            }
        }
    }

    private Bill map(ResultSet resultSet)
            throws SQLException {

        return new Bill(
                resultSet.getLong("bill_id"),
                resultSet.getString("invoice_number"),

                resultSet.getLong("appointment_id"),
                resultSet.getString("appointment_number"),
                resultSet.getString("patient_name"),

                resultSet.getBigDecimal("consultation_fee"),
                resultSet.getBigDecimal("treatment_fee"),
                resultSet.getBigDecimal("discount_amount"),
                resultSet.getBigDecimal("tax_amount"),
                resultSet.getBigDecimal("total_amount"),

                PaymentStatus.valueOf(
                        resultSet.getString(
                                "payment_status"
                        )
                ),

                resultSet.getString("discount_reason"),

                resultSet.getLong("created_by"),

                resultSet.getTimestamp(
                        "created_at"
                ).toLocalDateTime(),

                resultSet.getTimestamp(
                        "updated_at"
                ).toLocalDateTime()
        );
    }

    private void setNullableString(
            PreparedStatement statement,
            int index,
            String value
    ) throws SQLException {

        if (value == null || value.isBlank()) {
            statement.setNull(
                    index,
                    Types.VARCHAR
            );
        } else {
            statement.setString(
                    index,
                    value
            );
        }
    }
}