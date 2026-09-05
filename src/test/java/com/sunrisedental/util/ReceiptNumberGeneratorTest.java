package com.sunrisedental.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiptNumberGeneratorTest {

    @Test
    void generatesUniqueReceiptNumbers() {
        String first = ReceiptNumberGenerator.generate();
        String second = ReceiptNumberGenerator.generate();

        assertTrue(first.matches(
                "RCP-\\d{8}-[A-Z2-9]{6}"
        ));

        assertNotEquals(first, second);
    }
}
