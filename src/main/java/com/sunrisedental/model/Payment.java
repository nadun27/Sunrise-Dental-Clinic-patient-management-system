package com.sunrisedental.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Payment(
        long paymentId,
        String receiptNumber,
        long billId,
        String invoiceNumber,
        String patientName,
        BigDecimal amount,
        PaymentMethod paymentMethod,
        String referenceNumber,
        LocalDateTime paidAt,
        long receivedBy
) {
}
