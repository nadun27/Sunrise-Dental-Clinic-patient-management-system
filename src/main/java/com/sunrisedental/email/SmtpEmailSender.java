package com.sunrisedental.email;

import com.sunrisedental.config.AppConfig;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class SmtpEmailSender implements EmailSender {

    private final Session session;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailSender() {
        String host = AppConfig.getRequired("mail.host");
        int port = AppConfig.getInt("mail.port", 587);
        String username =
                AppConfig.getRequired("mail.username");
        String password =
                AppConfig.getRequired("mail.password");

        fromAddress = AppConfig.getRequired(
                "mail.from.address"
        );

        fromName = AppConfig.getRequired(
                "mail.from.name"
        );

        Properties properties = new Properties();
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", port);
        properties.put("mail.smtp.auth", "true");
        properties.put(
                "mail.smtp.starttls.enable",
                Boolean.toString(
                        AppConfig.getBoolean(
                                "mail.starttls",
                                true
                        )
                )
        );
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");

        session = Session.getInstance(
                properties,
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication
                    getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                username,
                                password
                        );
                    }
                }
        );
    }

    @Override
    public void send(EmailMessage message)
            throws Exception {

        MimeMessage mimeMessage = new MimeMessage(session);

        mimeMessage.setFrom(
                new InternetAddress(
                        fromAddress,
                        fromName,
                        StandardCharsets.UTF_8.name()
                )
        );

        mimeMessage.setRecipient(
                Message.RecipientType.TO,
                new InternetAddress(
                        message.recipient(),
                        true
                )
        );

        mimeMessage.setSubject(
                message.subject(),
                StandardCharsets.UTF_8.name()
        );

        mimeMessage.setText(
                message.body(),
                StandardCharsets.UTF_8.name()
        );

        Transport.send(mimeMessage);
    }
}
