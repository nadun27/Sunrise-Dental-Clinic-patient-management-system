package com.sunrisedental.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceNumberGeneratorTest {

    @Test
    void generatesInvoiceNumberInExpectedFormat() {
        String invoiceNumber =
                InvoiceNumberGenerator.generate();

        assertTrue(
                invoiceNumber.matches(
                        "INV-\\d{8}-[A-HJ-NP-Z2-9]{6}"
                )
        );
    }
}