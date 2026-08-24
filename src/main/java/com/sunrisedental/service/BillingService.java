package com.sunrisedental.service;

import com.sunrisedental.dao.AppointmentDao;
import com.sunrisedental.dao.BillDao;
import com.sunrisedental.dto.request.BillRequest;
import com.sunrisedental.exception.NotFoundException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.Bill;
import com.sunrisedental.model.BillCalculation;
import com.sunrisedental.model.PaymentStatus;
import com.sunrisedental.service.billing.BillingStrategy;
import com.sunrisedental.util.InvoiceNumberGenerator;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

public class BillingService {

    private static final int NUMBER_GENERATION_ATTEMPTS = 10;

    private final BillDao billDao;
    private final AppointmentDao appointmentDao;
    private final BillingStrategy billingStrategy;

    public BillingService(
            BillDao billDao,
            AppointmentDao appointmentDao,
            BillingStrategy billingStrategy
    ) {
        this.billDao = billDao;
        this.appointmentDao = appointmentDao;
        this.billingStrategy = billingStrategy;
    }

    public Bill create(
            BillRequest request,
            long createdBy
    ) throws SQLException {

        if (request == null) {
            throw new ValidationException(
                    "Bill information is required"
            );
        }

        if (createdBy <= 0) {
            throw new ValidationException(
                    "Valid staff user is required"
            );
        }

        validateAppointmentId(
                request.appointmentId()
        );

        Appointment appointment =
                appointmentDao
                        .findById(request.appointmentId())
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Appointment was not found"
                                )
                        );

        if (appointment.status() !=
                AppointmentStatus.COMPLETED) {

            throw new ValidationException(
                    "A bill can only be created for " +
                            "a completed appointment"
            );
        }

        if (billDao.findByAppointmentId(
                appointment.appointmentId()
        ).isPresent()) {

            throw new ValidationException(
                    "A bill already exists for this appointment"
            );
        }

        BillCalculation calculation =
                billingStrategy.calculate(
                        appointment.consultationFee(),
                        appointment.treatmentFee(),
                        request.discountAmount()
                );

        String discountReason =
                validateDiscountReason(
                        calculation.discountAmount(),
                        request.discountReason()
                );

        String invoiceNumber =
                generateUniqueInvoiceNumber();

        Bill bill = new Bill(
                0,
                invoiceNumber,

                appointment.appointmentId(),
                null,
                null,

                appointment.consultationFee(),
                appointment.treatmentFee(),
                calculation.discountAmount(),
                calculation.taxAmount(),
                calculation.totalAmount(),

                PaymentStatus.UNPAID,
                discountReason,

                createdBy,
                null,
                null
        );

        return billDao.create(bill);
    }

    public Bill getById(
            long billId
    ) throws SQLException {

        validateBillId(billId);

        return billDao.findById(billId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Bill was not found"
                        )
                );
    }

    public Bill getByAppointmentId(
            long appointmentId
    ) throws SQLException {

        validateAppointmentId(appointmentId);

        return billDao
                .findByAppointmentId(appointmentId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Bill was not found"
                        )
                );
    }

    public Bill getByInvoiceNumber(
            String invoiceNumber
    ) throws SQLException {

        if (invoiceNumber == null ||
                invoiceNumber.isBlank()) {

            throw new ValidationException(
                    "Invoice number is required"
            );
        }

        return billDao
                .findByInvoiceNumber(
                        invoiceNumber
                                .trim()
                                .toUpperCase()
                )
                .orElseThrow(() ->
                        new NotFoundException(
                                "Bill was not found"
                        )
                );
    }

    public List<Bill> search(
            String searchTerm,
            String paymentStatusValue
    ) throws SQLException {

        String normalizedTerm =
                searchTerm == null
                        ? ""
                        : searchTerm.trim();

        if (normalizedTerm.length() > 100) {
            throw new ValidationException(
                    "Search term cannot exceed 100 characters"
            );
        }

        PaymentStatus paymentStatus = null;

        if (paymentStatusValue != null &&
                !paymentStatusValue.isBlank()) {

            try {
                paymentStatus =
                        PaymentStatus.valueOf(
                                paymentStatusValue
                                        .trim()
                                        .toUpperCase()
                        );

            } catch (IllegalArgumentException exception) {
                throw new ValidationException(
                        "Select a valid payment status"
                );
            }
        }

        return billDao.search(
                normalizedTerm,
                paymentStatus
        );
    }

    private String generateUniqueInvoiceNumber()
            throws SQLException {

        for (
                int attempt = 0;
                attempt < NUMBER_GENERATION_ATTEMPTS;
                attempt++
        ) {
            String invoiceNumber =
                    InvoiceNumberGenerator.generate();

            if (!billDao.invoiceNumberExists(
                    invoiceNumber
            )) {
                return invoiceNumber;
            }
        }

        throw new SQLException(
                "A unique invoice number " +
                        "could not be generated"
        );
    }

    private String validateDiscountReason(
            BigDecimal discountAmount,
            String discountReason
    ) {
        boolean hasDiscount =
                discountAmount.compareTo(
                        BigDecimal.ZERO
                ) > 0;

        if (!hasDiscount) {
            return null;
        }

        if (discountReason == null ||
                discountReason.isBlank()) {

            throw new ValidationException(
                    "Discount reason is required " +
                            "when a discount is applied"
            );
        }

        String normalizedReason =
                discountReason.trim();

        if (normalizedReason.length() > 255) {
            throw new ValidationException(
                    "Discount reason cannot exceed " +
                            "255 characters"
            );
        }

        return normalizedReason;
    }

    private void validateBillId(long billId) {
        if (billId <= 0) {
            throw new ValidationException(
                    "Select a valid bill"
            );
        }
    }

    private void validateAppointmentId(
            long appointmentId
    ) {
        if (appointmentId <= 0) {
            throw new ValidationException(
                    "Select a valid appointment"
            );
        }
    }
}