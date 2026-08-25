package com.sunrisedental.service;

import com.sunrisedental.dao.BillDao;
import com.sunrisedental.dao.PaymentDao;
import com.sunrisedental.dto.request.PaymentRequest;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Bill;
import com.sunrisedental.model.Payment;
import com.sunrisedental.model.PaymentMethod;
import com.sunrisedental.model.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentServiceTest {

    @Test
    void recordsFullCashPayment() throws Exception {
        FakePaymentDao paymentDao =
                new FakePaymentDao();

        PaymentService service = service(
                PaymentStatus.UNPAID,
                paymentDao
        );

        Payment payment = service.record(
                new PaymentRequest(
                        1,
                        new BigDecimal("4500.00"),
                        "CASH",
                        null
                ),
                1
        );

        assertEquals(
                new BigDecimal("4500.00"),
                payment.amount()
        );

        assertEquals(
                PaymentMethod.CASH,
                payment.paymentMethod()
        );

        assertTrue(
                payment.receiptNumber()
                        .startsWith("RCP-")
        );
    }

    @Test
    void recordsPartialPayment() throws Exception {
        FakePaymentDao paymentDao =
                new FakePaymentDao();

        PaymentService service = service(
                PaymentStatus.UNPAID,
                paymentDao
        );

        Payment payment = service.record(
                new PaymentRequest(
                        1,
                        new BigDecimal("1000.00"),
                        "CARD",
                        "CARD-12345"
                ),
                1
        );

        assertEquals(
                new BigDecimal("1000.00"),
                payment.amount()
        );

        assertEquals(
                "CARD-12345",
                payment.referenceNumber()
        );
    }

    @Test
    void rejectsPaymentAboveRemainingBalance() {
        FakePaymentDao paymentDao =
                new FakePaymentDao();

        paymentDao.totalPaid =
                new BigDecimal("1000.00");

        PaymentService service = service(
                PaymentStatus.PARTIALLY_PAID,
                paymentDao
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.record(
                        new PaymentRequest(
                                1,
                                new BigDecimal("4000.00"),
                                "CASH",
                                null
                        ),
                        1
                )
        );

        assertEquals(
                "Payment amount cannot exceed " +
                        "the remaining balance",
                exception.getMessage()
        );
    }

    @Test
    void requiresReferenceForCardPayment() {
        PaymentService service = service(
                PaymentStatus.UNPAID,
                new FakePaymentDao()
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.record(
                        new PaymentRequest(
                                1,
                                new BigDecimal("500.00"),
                                "CARD",
                                ""
                        ),
                        1
                )
        );

        assertEquals(
                "Reference number is required " +
                        "for non-cash payments",
                exception.getMessage()
        );
    }

    @Test
    void rejectsPaymentForPaidBill() {
        PaymentService service = service(
                PaymentStatus.PAID,
                new FakePaymentDao()
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.record(
                        new PaymentRequest(
                                1,
                                new BigDecimal("500.00"),
                                "CASH",
                                null
                        ),
                        1
                )
        );

        assertEquals(
                "This bill is already fully paid",
                exception.getMessage()
        );
    }

    private PaymentService service(
            PaymentStatus status,
            FakePaymentDao paymentDao
    ) {
        return new PaymentService(
                paymentDao,
                new FakeBillDao(status)
        );
    }

    private static Bill bill(PaymentStatus status) {
        return new Bill(
                1,
                "INV-TEST-001",
                1,
                "APT-TEST-001",
                "Nimal Perera",
                new BigDecimal("2500.00"),
                new BigDecimal("2500.00"),
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                new BigDecimal("4500.00"),
                status,
                "Loyal patient discount",
                1,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }

    private static class FakeBillDao
            implements BillDao {

        private final Bill bill;

        private FakeBillDao(PaymentStatus status) {
            bill = bill(status);
        }

        @Override
        public Bill create(Bill bill) {
            return bill;
        }

        @Override
        public Optional<Bill> findById(long billId) {
            return billId == 1
                    ? Optional.of(bill)
                    : Optional.empty();
        }

        @Override
        public Optional<Bill> findByAppointmentId(
                long appointmentId
        ) {
            return Optional.empty();
        }

        @Override
        public Optional<Bill> findByInvoiceNumber(
                String invoiceNumber
        ) {
            return Optional.empty();
        }

        @Override
        public List<Bill> search(
                String searchTerm,
                PaymentStatus paymentStatus
        ) {
            return List.of(bill);
        }

        @Override
        public boolean invoiceNumberExists(
                String invoiceNumber
        ) {
            return false;
        }
    }

    private static class FakePaymentDao
            implements PaymentDao {

        private final List<Payment> payments =
                new ArrayList<>();

        private BigDecimal totalPaid =
                BigDecimal.ZERO;

        @Override
        public Payment create(Payment source) {
            Payment saved = new Payment(
                    payments.size() + 1,
                    source.receiptNumber(),
                    source.billId(),
                    "INV-TEST-001",
                    "Nimal Perera",
                    source.amount(),
                    source.paymentMethod(),
                    source.referenceNumber(),
                    LocalDateTime.now(),
                    source.receivedBy()
            );

            payments.add(saved);
            totalPaid = totalPaid.add(saved.amount());
            return saved;
        }

        @Override
        public Optional<Payment> findById(
                long paymentId
        ) {
            return payments.stream()
                    .filter(payment ->
                            payment.paymentId() == paymentId
                    )
                    .findFirst();
        }

        @Override
        public Optional<Payment> findByReceiptNumber(
                String receiptNumber
        ) {
            return payments.stream()
                    .filter(payment ->
                            payment.receiptNumber()
                                    .equals(receiptNumber)
                    )
                    .findFirst();
        }

        @Override
        public List<Payment> findByBillId(long billId) {
            return payments.stream()
                    .filter(payment ->
                            payment.billId() == billId
                    )
                    .toList();
        }

        @Override
        public BigDecimal totalPaidForBill(long billId) {
            return totalPaid;
        }

        @Override
        public boolean receiptNumberExists(
                String receiptNumber
        ) {
            return false;
        }
    }
}
