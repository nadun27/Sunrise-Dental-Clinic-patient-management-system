package com.sunrisedental.dto.request;

import java.math.BigDecimal;

public record StaffRequest(
        String username,
        String password,
        String confirmPassword,
        String fullName,
        String email,
        String contactNumber,
        String role,
        String registrationNumber,
        String specialization,
        BigDecimal consultationFee
) {
}
