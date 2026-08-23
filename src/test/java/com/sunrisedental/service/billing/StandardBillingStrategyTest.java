package com.sunrisedental.service.billing;

import com.sunrisedental.exception.ValidationException;
import com.sunrisedental.model.BillCalculation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StandardBillingStrategyTest {

    @Test
    void calculatesSubtotalDiscountTaxAndTotal() {
        BillingStrategy strategy =
                new StandardBillingStrategy(
                        new BigDecimal("0.10")
                );

        BillCalculation calculation =
                strategy.calculate(
                        new BigDecimal("2500.00"),
                        new BigDecimal("8000.00"),
                        new BigDecimal("500.00")
                );

        assertEquals(
                new BigDecimal("10500.00"),
                calculation.subtotal()
        );

        assertEquals(
                new BigDecimal("500.00"),
                calculation.discountAmount()
        );

        assertEquals(
                new BigDecimal("10000.00"),
                calculation.taxableAmount()
        );

        assertEquals(
                new BigDecimal("1000.00"),
                calculation.taxAmount()
        );

        assertEquals(
                new BigDecimal("11000.00"),
                calculation.totalAmount()
        );
    }

    @Test
    void treatsNullDiscountAsZero() {
        BillingStrategy strategy =
                new StandardBillingStrategy(
                        BigDecimal.ZERO
                );

        BillCalculation calculation =
                strategy.calculate(
                        new BigDecimal("2500.00"),
                        new BigDecimal("6500.00"),
                        null
                );

        assertEquals(
                new BigDecimal("0.00"),
                calculation.discountAmount()
        );

        assertEquals(
                new BigDecimal("9000.00"),
                calculation.totalAmount()
        );
    }

    @Test
    void rejectsDiscountGreaterThanSubtotal() {
        BillingStrategy strategy =
                new StandardBillingStrategy(
                        BigDecimal.ZERO
                );

        ValidationException exception =
                assertThrows(
                        ValidationException.class,
                        () -> strategy.calculate(
                                new BigDecimal("2500.00"),
                                new BigDecimal("6500.00"),
                                new BigDecimal("10000.00")
                        )
                );

        assertEquals(
                "Discount cannot exceed the subtotal",
                exception.getMessage()
        );
    }

    @Test
    void rejectsNegativeTreatmentFee() {
        BillingStrategy strategy =
                new StandardBillingStrategy(
                        BigDecimal.ZERO
                );

        assertThrows(
                ValidationException.class,
                () -> strategy.calculate(
                        new BigDecimal("2500.00"),
                        new BigDecimal("-1.00"),
                        BigDecimal.ZERO
                )
        );
    }

    @Test
    void rejectsInvalidTaxRate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StandardBillingStrategy(
                        new BigDecimal("1.10")
                )
        );
    }
}