package com.sunrisedental.event;

import java.util.Map;

public record ClinicEvent(
        ClinicEventType type,
        long appointmentId,
        Map<String, String> values
) {
    public ClinicEvent {
        values = values == null
                ? Map.of()
                : Map.copyOf(values);
    }
}
