package com.sunrisedental.service;

import com.sunrisedental.dao.AppointmentDao;
import com.sunrisedental.dao.BillDao;
import com.sunrisedental.dto.request.BillRequest;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Appointment;
import com.sunrisedental.model.AppointmentStatus;
import com.sunrisedental.model.Bill;
import com.sunrisedental.model.PaymentStatus;
import com.sunrisedental.service.billing.StandardBillingStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillingServiceTest {

    @Test
    void createsBillForCompletedAppointment()
            throws SQLException {

        FakeBillDao billDao = new FakeBillDao();

        BillingService service =
                service(
                        AppointmentStatus.COMPLETED,
                        billDao
                );

        Bill bill = service.create(
                new BillRequest(
                        1,
                        BigDecimal.ZERO,
                        null
                ),
                1
        );

        assertEquals(
                new BigDecimal("10500.00"),
                bill.totalAmount()
        );

        assertEquals(
                PaymentStatus.UNPAID,
                bill.paymentStatus()
        );

        assertTrue(
                bill.invoiceNumber()
                        .startsWith("INV-")
        );
    }

    @Test
    void appliesDiscountWithReason()
            throws SQLException {

        FakeBillDao billDao = new FakeBillDao();

        BillingService service =
                service(
                        AppointmentStatus.COMPLETED,
                        billDao
                );

        Bill bill = service.create(
                new BillRequest(
                        1,
                        new BigDecimal("500.00"),
                        "Loyal patient discount"
                ),
                1
        );

        assertEquals(
                new BigDecimal("500.00"),
                bill.discountAmount()
        );

        assertEquals(
                new BigDecimal("10000.00"),
                bill.totalAmount()
        );

        assertEquals(
                "Loyal patient discount",
                bill.discountReason()
        );
    }

    @Test
    void rejectsBillForIncompleteAppointment() {
        FakeBillDao billDao = new FakeBillDao();

        BillingService service =
                service(
                        AppointmentStatus.CONFIRMED,
                        billDao
                );

        ValidationException exception =
                assertThrows(
                        ValidationException.class,
                        () -> service.create(
                                new BillRequest(
                                        1,
                                        BigDecimal.ZERO,
                                        null
                                ),
                                1
                        )
                );

        assertEquals(
                "A bill can only be created for " +
                        "a completed appointment",
                exception.getMessage()
        );
    }

    @Test
    void rejectsDuplicateBillForAppointment() {
        FakeBillDao billDao = new FakeBillDao();
        billDao.duplicateBill = true;

        BillingService service =
                service(
                        AppointmentStatus.COMPLETED,
                        billDao
                );

        ValidationException exception =
                assertThrows(
                        ValidationException.class,
                        () -> service.create(
                                new BillRequest(
                                        1,
                                        BigDecimal.ZERO,
                                        null
                                ),
                                1
                        )
                );

        assertEquals(
                "A bill already exists for this appointment",
                exception.getMessage()
        );
    }

    @Test
    void requiresReasonWhenDiscountIsApplied() {
        FakeBillDao billDao = new FakeBillDao();

        BillingService service =
                service(
                        AppointmentStatus.COMPLETED,
                        billDao
                );

        ValidationException exception =
                assertThrows(
                        ValidationException.class,
                        () -> service.create(
                                new BillRequest(
                                        1,
                                        new BigDecimal("500.00"),
                                        ""
                                ),
                                1
                        )
                );

        assertEquals(
                "Discount reason is required " +
                        "when a discount is applied",
                exception.getMessage()
        );
    }

    private BillingService service(
            AppointmentStatus appointmentStatus,
            FakeBillDao billDao
    ) {
        return new BillingService(
                billDao,
                new FakeAppointmentDao(
                        appointmentStatus
                ),
                new StandardBillingStrategy(
                        BigDecimal.ZERO
                )
        );
    }

    private static class FakeAppointmentDao
            implements AppointmentDao {

        private final Appointment appointment;

        private FakeAppointmentDao(
                AppointmentStatus status
        ) {
            LocalDateTime startAt =
                    LocalDateTime.now()
                            .minusHours(2);

            appointment = new Appointment(
                    1,
                    "APT-TEST-001",

                    1,
                    "PAT-TEST-001",
                    "Nimal Perera",
                    "0771234567",

                    1,
                    "Dr. Anjali Perera",

                    1,
                    "Dental Filling",
                    new BigDecimal("8000.00"),
                    60,

                    new BigDecimal("2500.00"),

                    startAt,
                    startAt.plusMinutes(60),
                    status,

                    "Tooth pain",
                    null,
                    null,

                    1,
                    startAt.minusDays(1),
                    startAt,
                    1
            );
        }

        @Override
        public Appointment create(
                Appointment appointment
        ) {
            return appointment;
        }

        @Override
        public Optional<Appointment> findById(
                long appointmentId
        ) {
            return appointmentId == 1
                    ? Optional.of(appointment)
                    : Optional.empty();
        }

        @Override
        public Optional<Appointment> findByNumber(
                String appointmentNumber
        ) {
            return Optional.empty();
        }

        @Override
        public List<Appointment> search(
                String searchTerm,
                LocalDate date,
                AppointmentStatus status
        ) {
            return List.of(appointment);
        }

        @Override
        public boolean hasDentistConflict(
                long dentistId,
                LocalDateTime startAt,
                LocalDateTime endAt,
                Long excludedAppointmentId
        ) {
            return false;
        }

        @Override
        public boolean hasPatientConflict(
                long patientId,
                LocalDateTime startAt,
                LocalDateTime endAt,
                Long excludedAppointmentId
        ) {
            return false;
        }

        @Override
        public boolean updateSchedule(
                Appointment appointment,
                int expectedVersion
        ) {
            return true;
        }

        @Override
        public boolean updateStatus(
                long appointmentId,
                AppointmentStatus status,
                String cancellationReason,
                int expectedVersion
        ) {
            return true;
        }
    }

    private static class FakeBillDao
            implements BillDao {

        private final List<Bill> bills =
                new ArrayList<>();

        private boolean duplicateBill;

        @Override
        public Bill create(Bill source) {
            Bill saved = new Bill(
                    bills.size() + 1,
                    source.invoiceNumber(),

                    source.appointmentId(),
                    "APT-TEST-001",
                    "Nimal Perera",

                    source.consultationFee(),
                    source.treatmentFee(),
                    source.discountAmount(),
                    source.taxAmount(),
                    source.totalAmount(),

                    source.paymentStatus(),
                    source.discountReason(),

                    source.createdBy(),
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );

            bills.add(saved);
            return saved;
        }

        @Override
        public Optional<Bill> findById(long billId) {
            return bills.stream()
                    .filter(bill ->
                            bill.billId() == billId
                    )
                    .findFirst();
        }

        @Override
        public Optional<Bill> findByAppointmentId(
                long appointmentId
        ) {
            if (duplicateBill) {
                return Optional.of(
                        existingBill(appointmentId)
                );
            }

            return bills.stream()
                    .filter(bill ->
                            bill.appointmentId() ==
                                    appointmentId
                    )
                    .findFirst();
        }

        @Override
        public Optional<Bill> findByInvoiceNumber(
                String invoiceNumber
        ) {
            return bills.stream()
                    .filter(bill ->
                            bill.invoiceNumber()
                                    .equals(invoiceNumber)
                    )
                    .findFirst();
        }

        @Override
        public List<Bill> search(
                String searchTerm,
                PaymentStatus paymentStatus
        ) {
            return List.copyOf(bills);
        }

        @Override
        public boolean invoiceNumberExists(
                String invoiceNumber
        ) {
            return bills.stream()
                    .anyMatch(bill ->
                            bill.invoiceNumber()
                                    .equals(invoiceNumber)
                    );
        }

        private Bill existingBill(
                long appointmentId
        ) {
            return new Bill(
                    1,
                    "INV-TEST-001",

                    appointmentId,
                    "APT-TEST-001",
                    "Nimal Perera",

                    new BigDecimal("2500.00"),
                    new BigDecimal("8000.00"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new BigDecimal("10500.00"),

                    PaymentStatus.UNPAID,
                    null,

                    1,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
        }
    }
}