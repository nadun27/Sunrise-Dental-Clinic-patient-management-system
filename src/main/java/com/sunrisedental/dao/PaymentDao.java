package com.sunrisedental.dao;

import com.sunrisedental.model.Payment;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface PaymentDao {

    Payment create(Payment payment) throws SQLException;

    Optional<Payment> findById(long paymentId)
            throws SQLException;

    Optional<Payment> findByReceiptNumber(
            String receiptNumber
    ) throws SQLException;

    List<Payment> findByBillId(long billId)
            throws SQLException;

    BigDecimal totalPaidForBill(long billId)
            throws SQLException;

    boolean receiptNumberExists(String receiptNumber)
            throws SQLException;
}
