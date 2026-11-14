package com.company.hr.attendance.notification;

import com.company.hr.attendance.notification.application.EmailGateway;
import com.company.hr.attendance.notification.application.PushGateway;
import com.company.hr.attendance.notification.application.ShiftNotificationService;
import com.company.hr.attendance.notification.infrastructure.PushRepository;
import com.company.hr.attendance.notification.infrastructure.SmtpEmailGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShiftNotificationServiceTests {
    private PushRepository repository;
    private PushGateway gateway;
    private EmailGateway emailGateway;
    private JdbcTemplate jdbc;
    private ShiftNotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(PushRepository.class);
        gateway = mock(PushGateway.class);
        emailGateway = mock(EmailGateway.class);
        jdbc = mock(JdbcTemplate.class);
        service = new ShiftNotificationService(repository, gateway, emailGateway, jdbc);
    }

    @Test
    void sendSchedulePublishedNotifications_sendsEmailAndPushAndLogs() throws Exception {
        UUID emp1 = UUID.randomUUID();
        when(repository.findTokensByEmployeeId(emp1)).thenReturn(List.of("token-123"));
        when(gateway.sendCustom(eq(emp1), eq("token-123"), anyString(), anyString(), anyMap(), any(), any()))
            .thenReturn(PushGateway.Result.SENT);
        when(emailGateway.sendEmail(eq("nv1@example.com"), anyString(), anyString())).thenReturn(true);

        when(jdbc.query(startsWith("SELECT email, display_name, locked"), any(RowMapper.class), eq(emp1)))
            .thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                RowMapper<Object> mapper = inv.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getString("email")).thenReturn("nv1@example.com");
                when(rs.getString("display_name")).thenReturn("Nguyễn Văn A");
                when(rs.getBoolean("locked")).thenReturn(false);
                return List.of(mapper.mapRow(rs, 0));
            });

        int sent = service.sendSchedulePublishedNotifications(Set.of(emp1), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22));

        assertThat(sent).isEqualTo(2);
        verify(repository).recordNotificationLog(eq(emp1), eq("SCHEDULE_PUBLISHED"), anyString(), anyString(), isNull(), any());
        verify(emailGateway).sendEmail(eq("nv1@example.com"), contains("Lịch làm việc đã được công bố"), contains("2026-11-16"));
    }

    @Test
    void sendShiftReminders_deduplicatesAndSendsEmailAndPush() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        Instant startsAt = Instant.parse("2026-11-16T01:00:00Z"); // 08:00 Asia/Ho_Chi_Minh

        when(jdbc.query(startsWith("SELECT ws.id"), any(RowMapper.class), any(), any()))
            .thenAnswer(inv -> {
                @SuppressWarnings("unchecked")
                RowMapper<Object> mapper = inv.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getObject("id", UUID.class)).thenReturn(scheduleId);
                when(rs.getObject("employee_id", UUID.class)).thenReturn(empId);
                when(rs.getTimestamp("starts_at")).thenReturn(Timestamp.from(startsAt));
                when(rs.getString("shift_name")).thenReturn("Ca Sáng");
                when(rs.getObject("work_date", LocalDate.class)).thenReturn(LocalDate.of(2026, 11, 16));
                when(rs.getString("email")).thenReturn("nv1@example.com");
                when(rs.getString("display_name")).thenReturn("Nguyễn Văn A");
                when(rs.getBoolean("locked")).thenReturn(false);
                return List.of(mapper.mapRow(rs, 0));
            });
        when(repository.hasReceivedShiftReminder(scheduleId, "SHIFT_REMINDER_30M")).thenReturn(false);
        when(repository.findTokensByEmployeeId(empId)).thenReturn(List.of("token-abc"));
        when(emailGateway.sendEmail(eq("nv1@example.com"), anyString(), anyString())).thenReturn(true);

        int count = service.sendShiftReminders(startsAt.minusSeconds(30 * 60));

        assertThat(count).isEqualTo(1);
        verify(repository).recordShiftReminder(eq(scheduleId), eq(empId), eq("SHIFT_REMINDER_30M"), any());
        verify(emailGateway).sendEmail(
            eq("nv1@example.com"),
            contains("Ca Sáng"),
            argThat(body -> body.contains("08:00") && body.contains("30 phút"))
        );
        verify(gateway).sendCustom(eq(empId), eq("token-abc"), anyString(), anyString(), anyMap(), any(), any());
    }

    @Test
    void smtpEmailGateway_sendsSimpleMailMessageWhenConfigured() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailGateway smtp = new SmtpEmailGateway(mailSender, true, "hr@shiftsync.vn", "hr@shiftsync.vn");

        assertThat(smtp.available()).isTrue();
        assertThat(smtp.sendEmail("emp@example.com", "Nhắc ca", "Nội dung nhắc ca")).isTrue();

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage msg = captor.getValue();
        assertThat(msg.getFrom()).isEqualTo("hr@shiftsync.vn");
        assertThat(msg.getTo()).containsExactly("emp@example.com");
        assertThat(msg.getSubject()).isEqualTo("Nhắc ca");
        assertThat(msg.getText()).isEqualTo("Nội dung nhắc ca");

        SmtpEmailGateway disabled = new SmtpEmailGateway(mailSender, false, "hr@shiftsync.vn", "hr@shiftsync.vn");
        assertThat(disabled.available()).isFalse();
        assertThat(disabled.sendEmail("emp@example.com", "Nhắc ca", "Nội dung")).isFalse();
    }
}
