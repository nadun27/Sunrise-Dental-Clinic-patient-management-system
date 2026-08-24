package com.sunrisedental.model;

import java.math.BigDecimal;

public record BillCalculation(
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxableAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount
) {
}