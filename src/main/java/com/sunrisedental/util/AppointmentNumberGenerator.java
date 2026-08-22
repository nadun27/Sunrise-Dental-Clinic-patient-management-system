package com.sunrisedental.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class AppointmentNumberGenerator {

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private static final String CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.BASIC_ISO_DATE;

    private AppointmentNumberGenerator() {
    }

    public static String generate() {

        StringBuilder number =
                new StringBuilder("APT-")
                        .append(
                                LocalDate.now().format(
                                        DATE_FORMAT
                                )
                        )
                        .append("-");

        for (int index = 0; index < 6; index++) {
            number.append(
                    CHARACTERS.charAt(
                            RANDOM.nextInt(
                                    CHARACTERS.length()
                            )
                    )
            );
        }

        return number.toString();
    }
}