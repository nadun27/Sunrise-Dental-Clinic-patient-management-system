package com.sunrisedental.model;

import java.math.BigDecimal;

public record Treatment(
        long treatmentTypeId,
        String treatmentCode,
        String treatmentName,
        String description,
        BigDecimal defaultFee,
        int defaultDurationMinutes,
        boolean active
) {
}