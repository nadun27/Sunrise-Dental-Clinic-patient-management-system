package com.sunrisedental.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Bill(
        long billId,
        String invoiceNumber,

        long appointmentId,
        String appointmentNumber,
        String patientName,

        BigDecimal consultationFee,
        BigDecimal treatmentFee,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,

        PaymentStatus paymentStatus,
        String discountReason,

        long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}