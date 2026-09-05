package com.sunrisedental.dto.request;

import java.math.BigDecimal;

public record PaymentRequest(
        long billId,
        BigDecimal amount,
        String paymentMethod,
        String referenceNumber
) {
}
