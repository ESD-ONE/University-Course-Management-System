package com.ucms.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email channel. With ucms.mail.enabled=false (the default) messages are logged instead
 * of sent, so the in-app channel works with no SMTP server configured. Send failures are
 * logged, never propagated — email is best-effort; the in-app notification is the
 * record.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;

    public EmailSender(JavaMailSender mailSender, @Value("${ucms.mail.enabled:false}") boolean enabled,
                       @Value("${ucms.mail.from:no-reply@ucms.local}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    /** Returns true if the message was handed to the SMTP server. */
    public boolean send(String to, String subject, String body) {
        if (to == null || to.isBlank()) return false;
        if (!enabled) {
            log.info("[email disabled] to={} subject={}", to, subject);
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("Email to {} failed: {}", to, ex.getMessage());
            return false;
        }
    }
}
