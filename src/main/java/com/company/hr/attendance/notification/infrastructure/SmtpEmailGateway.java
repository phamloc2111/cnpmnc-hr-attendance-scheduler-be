package com.company.hr.attendance.notification.infrastructure;

import com.company.hr.attendance.notification.application.EmailGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailGateway implements EmailGateway {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailGateway.class);

    private final JavaMailSender mailSender;
    private final boolean emailEnabled;
    private final String smtpUsername;
    private final String fromAddress;

    public SmtpEmailGateway(
        @Autowired(required = false) JavaMailSender mailSender,
        @Value("${app.notifications.email-enabled:true}") boolean emailEnabled,
        @Value("${spring.mail.username:}") String smtpUsername,
        @Value("${app.notifications.email-from:no-reply@shiftsync.local}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.emailEnabled = emailEnabled;
        this.smtpUsername = smtpUsername == null ? "" : smtpUsername.trim();
        this.fromAddress = (fromAddress == null || fromAddress.isBlank())
            ? (this.smtpUsername.isEmpty() ? "no-reply@shiftsync.local" : this.smtpUsername)
            : fromAddress.trim();
    }

    @Override
    public boolean available() {
        return emailEnabled && mailSender != null && !smtpUsername.isEmpty();
    }

    @Override
    public boolean sendEmail(String toEmail, String subject, String body) {
        if (toEmail == null || toEmail.isBlank()) {
            return false;
        }
        if (!available()) {
            log.debug("Email delivery skipped for {} (emailEnabled={}, configured={})",
                toEmail, emailEnabled, !smtpUsername.isEmpty());
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail.trim());
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            return true;
        } catch (Exception ex) {
            log.warn("Failed to send email notification to {}", toEmail, ex);
            return false;
        }
    }
}
