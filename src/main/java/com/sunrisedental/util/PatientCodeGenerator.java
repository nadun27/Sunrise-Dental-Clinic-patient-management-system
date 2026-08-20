package com.sunrisedental.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class PatientCodeGenerator {

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private static final String CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.BASIC_ISO_DATE;

    private PatientCodeGenerator() {
        // Prevent object creation
    }

    public static String generate() {

        StringBuilder code = new StringBuilder();

        code.append("PAT-");
        code.append(LocalDate.now().format(DATE_FORMAT));
        code.append("-");

        for (int i = 0; i < 6; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            code.append(CHARACTERS.charAt(index));
        }

        return code.toString();
    }
}