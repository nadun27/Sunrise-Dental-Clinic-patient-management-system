package com.sunrisedental.dto.request;

import java.math.BigDecimal;

public record BillRequest(
        long appointmentId,
        BigDecimal discountAmount,
        String discountReason
) {
}