package com.sunrisedental.dao;

import com.sunrisedental.model.Bill;
import com.sunrisedental.model.PaymentStatus;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface BillDao {

    Bill create(Bill bill) throws SQLException;

    Optional<Bill> findById(
            long billId
    ) throws SQLException;

    Optional<Bill> findByAppointmentId(
            long appointmentId
    ) throws SQLException;

    Optional<Bill> findByInvoiceNumber(
            String invoiceNumber
    ) throws SQLException;

    List<Bill> search(
            String searchTerm,
            PaymentStatus paymentStatus
    ) throws SQLException;

    boolean invoiceNumberExists(
            String invoiceNumber
    ) throws SQLException;
}