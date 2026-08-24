package com.sunrisedental.service.billing;

import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.BillCalculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class StandardBillingStrategy
        implements BillingStrategy {

    private static final BigDecimal ZERO =
            new BigDecimal("0.00");

    private final BigDecimal taxRate;

    public StandardBillingStrategy(BigDecimal taxRate) {
        if (taxRate == null ||
                taxRate.compareTo(BigDecimal.ZERO) < 0 ||
                taxRate.compareTo(BigDecimal.ONE) > 0) {

            throw new IllegalArgumentException(
                    "Tax rate must be between 0 and 1"
            );
        }

        this.taxRate = taxRate;
    }

    @Override
    public BillCalculation calculate(
            BigDecimal consultationFee,
            BigDecimal treatmentFee,
            BigDecimal discountAmount
    ) {
        BigDecimal validConsultationFee =
                validateMoney(
                        consultationFee,
                        "Consultation fee"
                );

        BigDecimal validTreatmentFee =
                validateMoney(
                        treatmentFee,
                        "Treatment fee"
                );

        BigDecimal validDiscount =
                discountAmount == null
                        ? ZERO
                        : validateMoney(
                        discountAmount,
                        "Discount amount"
                );

        BigDecimal subtotal =
                validConsultationFee
                        .add(validTreatmentFee)
                        .setScale(2, RoundingMode.HALF_UP);

        if (validDiscount.compareTo(subtotal) > 0) {
            throw new ValidationException(
                    "Discount cannot exceed the subtotal"
            );
        }

        BigDecimal taxableAmount =
                subtotal
                        .subtract(validDiscount)
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal taxAmount =
                taxableAmount
                        .multiply(taxRate)
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalAmount =
                taxableAmount
                        .add(taxAmount)
                        .setScale(2, RoundingMode.HALF_UP);

        return new BillCalculation(
                subtotal,
                validDiscount,
                taxableAmount,
                taxAmount,
                totalAmount
        );
    }

    private BigDecimal validateMoney(
            BigDecimal value,
            String fieldName
    ) {
        if (value == null) {
            throw new ValidationException(
                    fieldName + " is required"
            );
        }

        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException(
                    fieldName + " cannot be negative"
            );
        }

        return value.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }
}