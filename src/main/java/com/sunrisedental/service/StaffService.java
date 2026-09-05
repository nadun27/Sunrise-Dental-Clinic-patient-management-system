package com.sunrisedental.service;

import com.sunrisedental.dao.StaffDao;
import com.sunrisedental.dto.request.StaffRequest;
import com.sunrisedental.exception.ConflictException;
import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.Role;
import com.sunrisedental.model.StaffMember;
import com.sunrisedental.util.PasswordUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class StaffService {

    private static final Pattern NAME_PATTERN =
            Pattern.compile(
                    "^[\\p{L}][\\p{L} .'-]{1,99}$"
            );

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile(
                    "^[a-z][a-z0-9._-]{3,49}$"
            );

    private static final Pattern CONTACT_PATTERN =
            Pattern.compile(
                    "^(?:\\+94[1-9]\\d{8}|0[1-9]\\d{8})$"
            );

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile(
                    "^[A-Za-z0-9._%+-]+@" +
                            "[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
            );

    private static final Pattern REGISTRATION_PATTERN =
            Pattern.compile(
                    "^[A-Z0-9][A-Z0-9/.-]{2,49}$"
            );

    private static final BigDecimal MAXIMUM_FEE =
            new BigDecimal("99999999.99");

    private final StaffDao staffDao;

    public StaffService(StaffDao staffDao) {
        this.staffDao = staffDao;
    }

    public StaffMember create(StaffRequest request)
            throws SQLException {

        ValidatedStaff valid = validateAndNormalize(request);

        if (staffDao.usernameExists(valid.username())) {
            throw new ConflictException(
                    "That username is already in use"
            );
        }

        if (valid.email() != null &&
                staffDao.emailExists(valid.email())) {

            throw new ConflictException(
                    "That email address is already in use"
            );
        }

        if (valid.registrationNumber() != null &&
                staffDao.registrationNumberExists(
                        valid.registrationNumber()
                )) {

            throw new ConflictException(
                    "That dentist registration number is already in use"
            );
        }

        String passwordHash =
                PasswordUtil.hash(valid.password());

        StaffMember staffMember = new StaffMember(
                0,
                valid.username(),
                valid.fullName(),
                valid.email(),
                valid.contactNumber(),
                valid.role(),
                true,
                valid.registrationNumber(),
                valid.specialization(),
                valid.consultationFee(),
                null
        );

        return staffDao.create(staffMember, passwordHash);
    }

    public List<StaffMember> search(
            String searchTerm,
            boolean includeInactive
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

        return staffDao.search(
                normalizedTerm,
                includeInactive
        );
    }

    private ValidatedStaff validateAndNormalize(
            StaffRequest request
    ) {
        if (request == null) {
            throw new ValidationException(
                    "Staff information is required"
            );
        }

        String username = normalizeUsername(request.username());
        String fullName = normalizeRequired(
                request.fullName(),
                "Staff name",
                2,
                100
        );

        if (!NAME_PATTERN.matcher(fullName).matches()) {
            throw new ValidationException(
                    "Staff name contains invalid characters"
            );
        }

        String email = normalizeOptional(
                request.email(),
                150,
                "Email"
        );

        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);

            if (!EMAIL_PATTERN.matcher(email).matches()) {
                throw new ValidationException(
                        "Enter a valid email address"
                );
            }
        }

        String contactNumber =
                normalizeOptionalContact(
                        request.contactNumber()
                );

        Role role = parseRole(request.role());
        validatePassword(request.password());

        if (!Objects.equals(
                request.password(),
                request.confirmPassword()
        )) {
            throw new ValidationException(
                    "Password confirmation does not match"
            );
        }

        String registrationNumber = null;
        String specialization = null;
        BigDecimal consultationFee = null;

        if (role == Role.DENTIST) {
            registrationNumber =
                    normalizeRegistrationNumber(
                            request.registrationNumber()
                    );

            specialization = normalizeOptional(
                    request.specialization(),
                    100,
                    "Specialization"
            );

            consultationFee = normalizeFee(
                    request.consultationFee()
            );
        }

        return new ValidatedStaff(
                username,
                request.password(),
                fullName,
                email,
                contactNumber,
                role,
                registrationNumber,
                specialization,
                consultationFee
        );
    }

    private String normalizeUsername(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    "Username is required"
            );
        }

        String normalized =
                value.trim().toLowerCase(Locale.ROOT);

        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException(
                    "Username must be 4 to 50 characters, start " +
                            "with a letter and contain only letters, " +
                            "numbers, dots, hyphens or underscores"
            );
        }

        return normalized;
    }

    private Role parseRole(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    "Staff role is required"
            );
        }

        try {
            return Role.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException exception) {
            throw new ValidationException(
                    "Select a valid staff role"
            );
        }
    }

    private void validatePassword(String password) {
        if (password == null ||
                password.isBlank() ||
                password.length() < 10) {

            throw new ValidationException(
                    "Password must contain at least 10 characters"
            );
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ValidationException(
                    "Password is too long"
            );
        }
    }

    private String normalizeOptionalContact(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized =
                value.replaceAll("[\\s()-]", "");

        if (!CONTACT_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException(
                    "Enter a valid Sri Lankan contact number"
            );
        }

        return normalized;
    }

    private String normalizeRegistrationNumber(String value) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    "Dentist registration number is required"
            );
        }

        String normalized =
                value.trim().toUpperCase(Locale.ROOT);

        if (!REGISTRATION_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException(
                    "Dentist registration number must contain " +
                            "3 to 50 letters, numbers, dots, " +
                            "hyphens or slashes"
            );
        }

        return normalized;
    }

    private BigDecimal normalizeFee(BigDecimal value) {
        if (value == null) {
            throw new ValidationException(
                    "Consultation fee is required for a dentist"
            );
        }

        if (value.compareTo(BigDecimal.ZERO) < 0 ||
                value.compareTo(MAXIMUM_FEE) > 0) {

            throw new ValidationException(
                    "Consultation fee must be between 0 and " +
                            "99,999,999.99"
            );
        }

        if (value.stripTrailingZeros().scale() > 2) {
            throw new ValidationException(
                    "Consultation fee cannot contain more than " +
                            "two decimal places"
            );
        }

        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private String normalizeRequired(
            String value,
            String fieldName,
            int minimumLength,
            int maximumLength
    ) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(
                    fieldName + " is required"
            );
        }

        String normalized =
                value.trim().replaceAll("\\s+", " ");

        if (normalized.length() < minimumLength ||
                normalized.length() > maximumLength) {

            throw new ValidationException(
                    fieldName + " must contain between " +
                            minimumLength + " and " +
                            maximumLength + " characters"
            );
        }

        return normalized;
    }

    private String normalizeOptional(
            String value,
            int maximumLength,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized =
                value.trim().replaceAll("\\s+", " ");

        if (normalized.length() > maximumLength) {
            throw new ValidationException(
                    fieldName + " cannot exceed " +
                            maximumLength + " characters"
            );
        }

        return normalized;
    }

    private record ValidatedStaff(
            String username,
            String password,
            String fullName,
            String email,
            String contactNumber,
            Role role,
            String registrationNumber,
            String specialization,
            BigDecimal consultationFee
    ) {
    }
}
