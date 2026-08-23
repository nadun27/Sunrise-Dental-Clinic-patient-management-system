package com.sunrisedental.service.billing;

import com.sunrisedental.model.BillCalculation;

import java.math.BigDecimal;

public interface BillingStrategy {

    BillCalculation calculate(
            BigDecimal consultationFee,
            BigDecimal treatmentFee,
            BigDecimal discountAmount
    );
}