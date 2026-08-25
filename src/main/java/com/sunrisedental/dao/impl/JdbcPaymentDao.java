package com.sunrisedental.dao.impl;

import com.sunrisedental.config.DatabaseConfig;
import com.sunrisedental.dao.PaymentDao;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Payment;
import com.sunrisedental.model.PaymentMethod;
import com.sunrisedental.model.PaymentStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcPaymentDao implements PaymentDao {

    private static final String SELECT_DETAILS = """
            SELECT pay.payment_id,
                   pay.receipt_number,
                   pay.bill_id,
                   b.invoice_number,
                   p.full_name AS patient_name,
                   pay.amount,
                   pay.payment_method,
                   pay.reference_number,
                   pay.paid_at,
                   pay.received_by
            FROM payments pay
            INNER JOIN bills b
                    ON b.bill_id = pay.bill_id
            INNER JOIN appointments a
                    ON a.appointment_id = b.appointment_id
            INNER JOIN patients p
                    ON p.patient_id = a.patient_id
            WHERE pay.voided_at IS NULL
            """;

    @Override
    public Payment create(Payment payment)
            throws SQLException {

        String lockBillSql = """
                SELECT total_amount, payment_status
                FROM bills
                WHERE bill_id = ?
                FOR UPDATE
                """;

        String totalPaidSql = """
                SELECT COALESCE(SUM(amount), 0.00)
                FROM payments
                WHERE bill_id = ?
                  AND voided_at IS NULL
                """;

        String insertSql = """
                INSERT INTO payments (
                    receipt_number,
                    bill_id,
                    amount,
                    payment_method,
                    reference_number,
                    received_by
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        String updateBillSql = """
                UPDATE bills
                SET payment_status = ?
                WHERE bill_id = ?
                """;

        long paymentId;

        try (Connection connection =
                     DatabaseConfig.getConnection()) {

            connection.setAutoCommit(false);

            try {
                BigDecimal billTotal;
                String currentStatus;

                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     lockBillSql
                             )) {

                    statement.setLong(
                            1,
                            payment.billId()
                    );

                    try (ResultSet resultSet =
                                 statement.executeQuery()) {

                        if (!resultSet.next()) {
                            throw new ValidationException(
                                    "Bill was not found"
                            );
                        }

                        billTotal =
                                resultSet.getBigDecimal(
                                        "total_amount"
                                );

                        currentStatus =
                                resultSet.getString(
                                        "payment_status"
                                );
                    }
                }

                if (PaymentStatus.PAID.name()
                        .equals(currentStatus)) {

                    throw new ValidationException(
                            "This bill is already fully paid"
                    );
                }

                if (PaymentStatus.VOID.name()
                        .equals(currentStatus)) {

                    throw new ValidationException(
                            "Payments cannot be recorded " +
                                    "for a void bill"
                    );
                }

                BigDecimal totalPaid;

                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     totalPaidSql
                             )) {

                    statement.setLong(
                            1,
                            payment.billId()
                    );

                    try (ResultSet resultSet =
                                 statement.executeQuery()) {

                        resultSet.next();
                        totalPaid = resultSet.getBigDecimal(1);
                    }
                }

                BigDecimal remaining =
                        billTotal.subtract(totalPaid);

                if (payment.amount()
                        .compareTo(remaining) > 0) {

                    throw new ValidationException(
                            "Payment amount cannot exceed " +
                                    "the remaining balance"
                    );
                }

                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     insertSql,
                                     Statement.RETURN_GENERATED_KEYS
                             )) {

                    statement.setString(
                            1,
                            payment.receiptNumber()
                    );

                    statement.setLong(
                            2,
                            payment.billId()
                    );

                    statement.setBigDecimal(
                            3,
                            payment.amount()
                    );

                    statement.setString(
                            4,
                            payment.paymentMethod().name()
                    );

                    setNullableString(
                            statement,
                            5,
                            payment.referenceNumber()
                    );

                    statement.setLong(
                            6,
                            payment.receivedBy()
                    );

                    statement.executeUpdate();

                    try (ResultSet keys =
                                 statement.getGeneratedKeys()) {

                        if (!keys.next()) {
                            throw new SQLException(
                                    "Payment ID was not generated"
                            );
                        }

                        paymentId = keys.getLong(1);
                    }
                }

                BigDecimal newTotalPaid =
                        totalPaid.add(payment.amount());

                PaymentStatus newStatus =
                        newTotalPaid.compareTo(billTotal) == 0
                                ? PaymentStatus.PAID
                                : PaymentStatus.PARTIALLY_PAID;

                try (PreparedStatement statement =
                             connection.prepareStatement(
                                     updateBillSql
                             )) {

                    statement.setString(
                            1,
                            newStatus.name()
                    );

                    statement.setLong(
                            2,
                            payment.billId()
                    );

                    statement.executeUpdate();
                }

                connection.commit();

            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;

            } finally {
                connection.setAutoCommit(true);
            }
        }

        return findById(paymentId)
                .orElseThrow(() ->
                        new SQLException(
                                "Created payment could not be retrieved"
                        )
                );
    }

    @Override
    public Optional<Payment> findById(
            long paymentId
    ) throws SQLException {

        String sql =
                SELECT_DETAILS +
                        " AND pay.payment_id = ? LIMIT 1";

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, paymentId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Payment> findByReceiptNumber(
            String receiptNumber
    ) throws SQLException {

        String sql =
                SELECT_DETAILS +
                        " AND pay.receipt_number = ? LIMIT 1";

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, receiptNumber);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next()
                        ? Optional.of(map(resultSet))
                        : Optional.empty();
            }
        }
    }

    @Override
    public List<Payment> findByBillId(
            long billId
    ) throws SQLException {

        String sql =
                SELECT_DETAILS +
                        " AND pay.bill_id = ?" +
                        " ORDER BY pay.paid_at DESC";

        List<Payment> payments = new ArrayList<>();

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, billId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                while (resultSet.next()) {
                    payments.add(map(resultSet));
                }
            }
        }

        return payments;
    }

    @Override
    public BigDecimal totalPaidForBill(
            long billId
    ) throws SQLException {

        String sql = """
                SELECT COALESCE(SUM(amount), 0.00)
                FROM payments
                WHERE bill_id = ?
                  AND voided_at IS NULL
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setLong(1, billId);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                resultSet.next();
                return resultSet.getBigDecimal(1);
            }
        }
    }

    @Override
    public boolean receiptNumberExists(
            String receiptNumber
    ) throws SQLException {

        String sql = """
                SELECT 1
                FROM payments
                WHERE receipt_number = ?
                LIMIT 1
                """;

        try (
                Connection connection =
                        DatabaseConfig.getConnection();

                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {
            statement.setString(1, receiptNumber);

            try (ResultSet resultSet =
                         statement.executeQuery()) {

                return resultSet.next();
            }
        }
    }

    private Payment map(ResultSet resultSet)
            throws SQLException {

        return new Payment(
                resultSet.getLong("payment_id"),
                resultSet.getString("receipt_number"),
                resultSet.getLong("bill_id"),
                resultSet.getString("invoice_number"),
                resultSet.getString("patient_name"),
                resultSet.getBigDecimal("amount"),
                PaymentMethod.valueOf(
                        resultSet.getString(
                                "payment_method"
                        )
                ),
                resultSet.getString("reference_number"),
                resultSet.getTimestamp("paid_at")
                        .toLocalDateTime(),
                resultSet.getLong("received_by")
        );
    }

    private void setNullableString(
            PreparedStatement statement,
            int index,
            String value
    ) throws SQLException {

        if (value == null || value.isBlank()) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.trim());
        }
    }
}
