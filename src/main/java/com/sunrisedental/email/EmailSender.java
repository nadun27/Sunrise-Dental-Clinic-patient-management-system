package com.sunrisedental.email;

@FunctionalInterface
public interface EmailSender {
    void send(EmailMessage message) throws Exception;
}
