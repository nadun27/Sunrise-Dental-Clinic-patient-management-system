package com.sunrisedental.service;

import com.sunrisedental.dao.BillDao;
import com.sunrisedental.dao.PaymentDao;
import com.sunrisedental.dto.request.PaymentRequest;
import com.sunrisedental.event.ClinicEvent;
import com.sunrisedental.event.ClinicEventPublisher;
import com.sunrisedental.event.ClinicEventType;
import com.sunrisedental.exception.NotFoundException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Bill;
import com.sunrisedental.model.Payment;
import com.sunrisedental.model.PaymentMethod;
import com.sunrisedental.model.PaymentStatus;
import com.sunrisedental.util.ReceiptNumberGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class PaymentService {

    private static final int NUMBER_GENERATION_ATTEMPTS = 10;

    private final PaymentDao paymentDao;
    private final BillDao billDao;

    public PaymentService(
            PaymentDao paymentDao,
            BillDao billDao
    ) {
        this.paymentDao = paymentDao;
        this.billDao = billDao;
    }

    public Payment record(
            PaymentRequest request,
            long receivedBy
    ) throws SQLException {

        if (request == null) {
            throw new ValidationException(
                    "Payment information is required"
            );
        }

        validatePositiveId(
                request.billId(),
                "Bill"
        );

        validatePositiveId(
                receivedBy,
                "Staff user"
        );

        Bill bill = billDao.findById(request.billId())
                .orElseThrow(() ->
                        new NotFoundException(
                                "Bill was not found"
                        )
                );

        if (bill.paymentStatus() == PaymentStatus.PAID) {
            throw new ValidationException(
                    "This bill is already fully paid"
            );
        }

        if (bill.paymentStatus() == PaymentStatus.VOID) {
            throw new ValidationException(
                    "Payments cannot be recorded for a void bill"
            );
        }

        BigDecimal amount = validateAmount(request.amount());

        BigDecimal totalPaid =
                paymentDao.totalPaidForBill(
                        bill.billId()
                );

        BigDecimal remaining =
                bill.totalAmount().subtract(totalPaid);

        if (amount.compareTo(remaining) > 0) {
            throw new ValidationException(
                    "Payment amount cannot exceed " +
                            "the remaining balance"
            );
        }

        PaymentMethod paymentMethod =
                parseMethod(request.paymentMethod());

        String referenceNumber =
                validateReference(
                        paymentMethod,
                        request.referenceNumber()
                );

        Payment payment = new Payment(
                0,
                generateUniqueReceiptNumber(),
                bill.billId(),
                null,
                null,
                amount,
                paymentMethod,
                referenceNumber,
                null,
                receivedBy
        );

        Payment recorded = paymentDao.create(payment);

        BigDecimal remainingAfterPayment =
                remaining.subtract(amount);

        ClinicEventPublisher.getInstance().publish(
                new ClinicEvent(
                        ClinicEventType.PAYMENT_RECEIVED,
                        bill.appointmentId(),
                        Map.of(
                                "receiptNumber",
                                recorded.receiptNumber(),
                                "invoiceNumber",
                                bill.invoiceNumber(),
                                "amount",
                                recorded.amount().toPlainString(),
                                "paymentMethod",
                                recorded.paymentMethod().name(),
                                "remainingBalance",
                                remainingAfterPayment.toPlainString()
                        )
                )
        );

        return recorded;
    }

    public Payment getById(long paymentId)
            throws SQLException {

        validatePositiveId(paymentId, "Payment");

        return paymentDao.findById(paymentId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Payment was not found"
                        )
                );
    }

    public List<Payment> getForBill(long billId)
            throws SQLException {

        validatePositiveId(billId, "Bill");

        if (billDao.findById(billId).isEmpty()) {
            throw new NotFoundException(
                    "Bill was not found"
            );
        }

        return paymentDao.findByBillId(billId);
    }

    public BigDecimal totalPaid(long billId)
            throws SQLException {

        validatePositiveId(billId, "Bill");
        return paymentDao.totalPaidForBill(billId);
    }

    public BigDecimal remainingBalance(long billId)
            throws SQLException {

        Bill bill = billDao.findById(billId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Bill was not found"
                        )
                );

        return bill.totalAmount()
                .subtract(
                        paymentDao.totalPaidForBill(
                                billId
                        )
                );
    }

    private BigDecimal validateAmount(
            BigDecimal amount
    ) {
        if (amount == null ||
                amount.compareTo(BigDecimal.ZERO) <= 0) {

            throw new ValidationException(
                    "Payment amount must be greater than zero"
            );
        }

        try {
            return amount.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );

        } catch (ArithmeticException exception) {
            throw new ValidationException(
                    "Payment amount can have a maximum " +
                            "of two decimal places"
            );
        }
    }

    private PaymentMethod parseMethod(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    "Payment method is required"
            );
        }

        try {
            return PaymentMethod.valueOf(
                    value.trim().toUpperCase()
            );

        } catch (IllegalArgumentException exception) {
            throw new ValidationException(
                    "Select a valid payment method"
            );
        }
    }

    private String validateReference(
            PaymentMethod method,
            String referenceNumber
    ) {
        String value = referenceNumber == null
                ? ""
                : referenceNumber.trim();

        if (method != PaymentMethod.CASH &&
                value.isBlank()) {

            throw new ValidationException(
                    "Reference number is required " +
                            "for non-cash payments"
            );
        }

        if (value.length() > 100) {
            throw new ValidationException(
                    "Reference number cannot exceed " +
                            "100 characters"
            );
        }

        return value.isBlank() ? null : value;
    }

    private String generateUniqueReceiptNumber()
            throws SQLException {

        for (
                int attempt = 0;
                attempt < NUMBER_GENERATION_ATTEMPTS;
                attempt++
        ) {
            String receiptNumber =
                    ReceiptNumberGenerator.generate();

            if (!paymentDao.receiptNumberExists(
                    receiptNumber
            )) {
                return receiptNumber;
            }
        }

        throw new SQLException(
                "A unique receipt number could not be generated"
        );
    }

    private void validatePositiveId(
            long value,
            String fieldName
    ) {
        if (value <= 0) {
            throw new ValidationException(
                    fieldName + " must be valid"
            );
        }
    }
}
