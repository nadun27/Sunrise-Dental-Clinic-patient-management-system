package com.sunrisedental.model;

import java.time.LocalDateTime;
import java.util.Map;

public record Notification(
        long notificationId,
        long appointmentId,
        String recipient,
        String templateCode,
        Map<String, String> payload,
        NotificationStatus status,
        int attemptCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime sentAt,
        String lastError,
        LocalDateTime createdAt
) {
    public Notification {
        payload = payload == null
                ? Map.of()
                : Map.copyOf(payload);
    }
}
