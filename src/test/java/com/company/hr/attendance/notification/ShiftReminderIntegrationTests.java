package com.company.hr.attendance.notification;

import com.company.hr.attendance.identity.domain.Account;
import com.company.hr.attendance.identity.domain.Role;
import com.company.hr.attendance.identity.infrastructure.AccountRepository;
import com.company.hr.attendance.identity.infrastructure.LoginSessionRepository;
import com.company.hr.attendance.notification.application.EmailGateway;
import com.company.hr.attendance.notification.application.PushGateway;
import com.company.hr.attendance.notification.application.ShiftNotificationService;
import com.company.hr.attendance.organization.domain.Position;
import com.company.hr.attendance.organization.infrastructure.PositionRepository;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.notifications.cron-secret=test-only-cron-secret-more-than-32-characters")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShiftReminderIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired PositionRepository positions;
    @Autowired ShiftTemplateRepository templates;
    @Autowired WorkScheduleRepository schedules;
    @Autowired ShiftNotificationService shiftNotifications;
    @Autowired PasswordEncoder passwords;

    @MockitoBean PushGateway gateway;
    @MockitoBean EmailGateway emailGateway;

    private static final String PASSWORD = "Test-password-123!";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private Account manager, employee;
    private String managerToken, employeeToken;
    private WorkSchedule duePublishedShift;
    private LocalDate scheduleDate;

    @BeforeEach
    void setup() throws Exception {
        cleanup();
        when(gateway.available()).thenReturn(true);
        when(emailGateway.available()).thenReturn(true);
        when(emailGateway.sendEmail(anyString(), anyString(), anyString())).thenReturn(true);

        manager = accounts.save(new Account("mgr.notif@example.com", passwords.encode(PASSWORD), "Quản lý Thông báo", Role.MANAGER));
        Position pos = positions.save(new Position("Thu ngân", manager.getId()));
        employee = new Account("emp.notif@example.com", passwords.encode(PASSWORD), "Nhân viên Nhận Tin", Role.EMPLOYEE);
        employee.updateDetails("emp.notif@example.com", "Nhân viên Nhận Tin", Role.EMPLOYEE, pos.getName(), List.of(), manager.getId());
        employee.setPositionId(pos.getId());
        employee = accounts.save(employee);

        // Pick a start time ~30 minutes from now (rounded cleanly to minute to satisfy ShiftTemplate/WorkSchedule)
        ZonedDateTime targetStart = ZonedDateTime.now(VN).plusMinutes(30).withSecond(0).withNano(0);
        scheduleDate = targetStart.toLocalDate();
        LocalTime startTime = targetStart.toLocalTime();
        LocalTime endTime = startTime.plusHours(4);
        boolean overnight = endTime.isBefore(startTime);

        var tpl = templates.save(new ShiftTemplate(
            "Ca Nhắc 30p", startTime, endTime, 0,
            manager.getId(), 1, pos.getName(), pos.getId(), overnight
        ));

        // Create unpublished shift first, then publish via /work-schedules/publish or save draft + published
        duePublishedShift = schedules.save(new WorkSchedule(employee.getId(), tpl, scheduleDate, VN, manager.getId()));

        managerToken = login("mgr.notif@example.com");
        employeeToken = login("emp.notif@example.com");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM notification_logs");
        jdbc.update("DELETE FROM shift_reminder_deliveries");
        schedules.deleteAll();
        templates.deleteAll();
        sessions.deleteAll();
        jdbc.update("DELETE FROM identity_account_skills");
        jdbc.update("UPDATE identity_accounts SET manager_id = NULL");
        accounts.deleteAll();
        positions.deleteAll();
    }

    private String login(String email) throws Exception {
        var res = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void publishEmitsScheduleNotification_andCronTriggersDeduped30MinReminder() throws Exception {
        // Unauthorized cron key rejected with 401
        mvc.perform(post("/api/v1/notification-jobs/shift-reminders")
                .header("X-Reminder-Cron-Key", "wrong-key"))
            .andExpect(status().isUnauthorized());

        // Before publishing, unpublished shift does NOT trigger 30m reminder
        mvc.perform(post("/api/v1/notification-jobs/shift-reminders")
                .header("X-Reminder-Cron-Key", "test-only-cron-secret-more-than-32-characters"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.queued").value(0));

        // Manager publishes schedule -> emits SchedulePublishedEvent -> records SCHEDULE_PUBLISHED log and sends email
        mvc.perform(post("/api/v1/work-schedules/publish")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "from", scheduleDate.toString(),
                    "to", scheduleDate.toString(),
                    "confirmOvertime", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publishedCount").value(1));

        verify(emailGateway, times(1)).sendEmail(
            eq("emp.notif@example.com"),
            contains("Lịch làm việc đã được công bố"),
            contains(scheduleDate.toString())
        );

        // Now cron triggers 30-min shift reminder once against real work_schedules query -> sends 30m reminder email
        mvc.perform(post("/api/v1/notification-jobs/shift-reminders")
                .header("X-Reminder-Cron-Key", "test-only-cron-secret-more-than-32-characters"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.queued").value(1));

        verify(emailGateway, times(1)).sendEmail(
            eq("emp.notif@example.com"),
            contains("Ca Nhắc 30p"),
            contains("30 phút")
        );

        // Second cron run must deduplicate via shift_reminder_deliveries -> 0 queued, no duplicate email
        mvc.perform(post("/api/v1/notification-jobs/shift-reminders")
                .header("X-Reminder-Cron-Key", "test-only-cron-secret-more-than-32-characters"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.queued").value(0));

        verify(emailGateway, times(1)).sendEmail(
            eq("emp.notif@example.com"),
            contains("Ca Nhắc 30p"),
            contains("30 phút")
        );

        // Employee fetches persisted notifications via GET /api/v1/notifications/me
        mvc.perform(get("/api/v1/notifications/me")
                .header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].kind", hasItem("SCHEDULE_PUBLISHED")))
            .andExpect(jsonPath("$[*].kind", hasItem("SHIFT_REMINDER_30M")))
            .andExpect(jsonPath("$[*].body", hasItem(containsString("Ca Nhắc 30p"))));

        // Direct service invocation outside exact 25-35m window returns 0
        assertThat(shiftNotifications.sendShiftReminders(Instant.now().minus(Duration.ofHours(3)))).isZero();
    }
}
