package com.sunrisedental.email;

public record EmailMessage(
        String recipient,
        String subject,
        String body
) {
}
