package com.sunrisedental.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ClinicReport(
        LocalDate fromDate,
        LocalDate toDate,
        long totalAppointments,
        long completedAppointments,
        long cancelledAppointments,
        long newPatients,
        long generatedBills,
        BigDecimal billedAmount,
        BigDecimal collectedAmount,
        BigDecimal outstandingAmount,
        List<AppointmentStatusTotal> appointmentStatuses,
        List<TreatmentTotal> treatments,
        List<DentistTotal> dentists
) {

    public ClinicReport {
        billedAmount = moneyOrZero(billedAmount);
        collectedAmount = moneyOrZero(collectedAmount);
        outstandingAmount = moneyOrZero(outstandingAmount);

        appointmentStatuses =
                appointmentStatuses == null
                        ? List.of()
                        : List.copyOf(appointmentStatuses);

        treatments =
                treatments == null
                        ? List.of()
                        : List.copyOf(treatments);

        dentists =
                dentists == null
                        ? List.of()
                        : List.copyOf(dentists);
    }

    private static BigDecimal moneyOrZero(
            BigDecimal value
    ) {
        return value == null
                ? BigDecimal.ZERO
                : value;
    }

    public record AppointmentStatusTotal(
            String status,
            long appointmentCount
    ) {
    }

    public record TreatmentTotal(
            String treatmentName,
            long appointmentCount,
            BigDecimal billedAmount
    ) {
        public TreatmentTotal {
            billedAmount = moneyOrZero(billedAmount);
        }
    }

    public record DentistTotal(
            String dentistName,
            long appointmentCount,
            long completedCount
    ) {
    }
}