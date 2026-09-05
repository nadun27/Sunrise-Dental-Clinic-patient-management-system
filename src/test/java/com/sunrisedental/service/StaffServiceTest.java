package com.sunrisedental.service;

import com.sunrisedental.dao.StaffDao;
import com.sunrisedental.dto.request.StaffRequest;
import com.sunrisedental.exception.ConflictException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.StaffMember;
import com.sunrisedental.util.PasswordUtil;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffServiceTest {

    @Test
    void createsStaffAccountWithNormalizedInformation()
            throws SQLException {

        FakeStaffDao staffDao = new FakeStaffDao();
        StaffService service = new StaffService(staffDao);

        StaffMember created = service.create(
                new StaffRequest(
                        "  Reception.One  ",
                        "SecurePass123",
                        "SecurePass123",
                        "  Nimal   Perera  ",
                        "NIMAL@EXAMPLE.COM",
                        "077 123 4567",
                        "receptionist",
                        null,
                        null,
                        null
                )
        );

        assertEquals("reception.one", created.username());
        assertEquals("Nimal Perera", created.fullName());
        assertEquals("nimal@example.com", created.email());
        assertEquals("0771234567", created.contactNumber());
        assertEquals(Role.RECEPTIONIST, created.role());
        assertNull(created.registrationNumber());

        assertTrue(
                PasswordUtil.matches(
                        "SecurePass123",
                        staffDao.savedPasswordHash
                )
        );
    }

    @Test
    void createsLinkedDentistInformation()
            throws SQLException {

        FakeStaffDao staffDao = new FakeStaffDao();
        StaffService service = new StaffService(staffDao);

        StaffMember created = service.create(
                new StaffRequest(
                        "dentist.one",
                        "DentistPass123",
                        "DentistPass123",
                        "Dr Nadeesha Silva",
                        "nadeesha@example.com",
                        null,
                        "DENTIST",
                        " sldc-1024 ",
                        " General   Dentistry ",
                        new BigDecimal("2500")
                )
        );

        assertEquals(Role.DENTIST, created.role());
        assertEquals("SLDC-1024", created.registrationNumber());
        assertEquals("General Dentistry", created.specialization());
        assertEquals(
                new BigDecimal("2500.00"),
                created.consultationFee()
        );
    }

    @Test
    void rejectsDuplicateUsername() {
        FakeStaffDao staffDao = new FakeStaffDao();
        staffDao.usernames.add("cashier.one");

        StaffService service = new StaffService(staffDao);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> service.create(validCashierRequest())
        );

        assertEquals(
                "That username is already in use",
                exception.getMessage()
        );
    }

    @Test
    void rejectsDuplicateEmail() {
        FakeStaffDao staffDao = new FakeStaffDao();
        staffDao.emails.add("cashier@example.com");

        StaffService service = new StaffService(staffDao);

        assertThrows(
                ConflictException.class,
                () -> service.create(validCashierRequest())
        );
    }

    @Test
    void rejectsDentistWithoutRegistrationNumber() {
        StaffService service =
                new StaffService(new FakeStaffDao());

        StaffRequest request = new StaffRequest(
                "dentist.two",
                "DentistPass123",
                "DentistPass123",
                "Nadeesha Silva",
                null,
                null,
                "DENTIST",
                null,
                null,
                new BigDecimal("2500")
        );

        assertThrows(
                ValidationException.class,
                () -> service.create(request)
        );
    }

    @Test
    void rejectsPasswordConfirmationMismatch() {
        StaffService service =
                new StaffService(new FakeStaffDao());

        StaffRequest request = new StaffRequest(
                "cashier.one",
                "SecurePass123",
                "DifferentPass123",
                "Kasun Silva",
                null,
                null,
                "CASHIER",
                null,
                null,
                null
        );

        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> service.create(request)
        );

        assertEquals(
                "Password confirmation does not match",
                exception.getMessage()
        );
    }

    @Test
    void rejectsInvalidRole() {
        StaffService service =
                new StaffService(new FakeStaffDao());

        StaffRequest request = new StaffRequest(
                "staff.one",
                "SecurePass123",
                "SecurePass123",
                "Kasun Silva",
                null,
                null,
                "MANAGER",
                null,
                null,
                null
        );

        assertThrows(
                ValidationException.class,
                () -> service.create(request)
        );
    }

    @Test
    void rejectsInvalidContactNumber() {
        StaffService service =
                new StaffService(new FakeStaffDao());

        StaffRequest request = new StaffRequest(
                "cashier.one",
                "SecurePass123",
                "SecurePass123",
                "Kasun Silva",
                null,
                "12345",
                "CASHIER",
                null,
                null,
                null
        );

        assertThrows(
                ValidationException.class,
                () -> service.create(request)
        );
    }

    private StaffRequest validCashierRequest() {
        return new StaffRequest(
                "cashier.one",
                "SecurePass123",
                "SecurePass123",
                "Kasun Silva",
                "cashier@example.com",
                "0712345678",
                "CASHIER",
                null,
                null,
                null
        );
    }

    private static class FakeStaffDao implements StaffDao {

        private final List<StaffMember> staffMembers =
                new ArrayList<>();

        private final Set<String> usernames =
                new HashSet<>();

        private final Set<String> emails =
                new HashSet<>();

        private final Set<String> registrationNumbers =
                new HashSet<>();

        private String savedPasswordHash;

        @Override
        public StaffMember create(
                StaffMember staffMember,
                String passwordHash
        ) {
            savedPasswordHash = passwordHash;

            StaffMember saved = new StaffMember(
                    staffMembers.size() + 1L,
                    staffMember.username(),
                    staffMember.fullName(),
                    staffMember.email(),
                    staffMember.contactNumber(),
                    staffMember.role(),
                    staffMember.active(),
                    staffMember.registrationNumber(),
                    staffMember.specialization(),
                    staffMember.consultationFee(),
                    LocalDateTime.now()
            );

            staffMembers.add(saved);
            usernames.add(saved.username());

            if (saved.email() != null) {
                emails.add(saved.email());
            }

            if (saved.registrationNumber() != null) {
                registrationNumbers.add(
                        saved.registrationNumber()
                );
            }

            return saved;
        }

        @Override
        public List<StaffMember> search(
                String searchTerm,
                boolean includeInactive
        ) {
            return List.copyOf(staffMembers);
        }

        @Override
        public boolean usernameExists(String username) {
            return usernames.contains(username);
        }

        @Override
        public boolean emailExists(String email) {
            return emails.contains(email);
        }

        @Override
        public boolean registrationNumberExists(
                String registrationNumber
        ) {
            return registrationNumbers.contains(
                    registrationNumber
            );
        }
    }
}
