package com.company.hr.attendance.notification.application;

public interface EmailGateway {
    boolean available();
    boolean sendEmail(String toEmail, String subject, String body);
}
