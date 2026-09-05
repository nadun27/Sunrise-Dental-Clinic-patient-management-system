package com.sunrisedental.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class InvoiceNumberGenerator {

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private static final String CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.BASIC_ISO_DATE;

    private InvoiceNumberGenerator() {
    }

    public static String generate() {
        StringBuilder invoiceNumber =
                new StringBuilder("INV-")
                        .append(
                                LocalDate.now().format(
                                        DATE_FORMAT
                                )
                        )
                        .append("-");

        for (int index = 0; index < 6; index++) {
            invoiceNumber.append(
                    CHARACTERS.charAt(
                            RANDOM.nextInt(
                                    CHARACTERS.length()
                            )
                    )
            );
        }

        return invoiceNumber.toString();
    }
}