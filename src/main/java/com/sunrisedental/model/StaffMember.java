package com.sunrisedental.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StaffMember(
        long userId,
        String username,
        String fullName,
        String email,
        String contactNumber,
        Role role,
        boolean active,
        String registrationNumber,
        String specialization,
        BigDecimal consultationFee,
        LocalDateTime createdAt
) {
}
