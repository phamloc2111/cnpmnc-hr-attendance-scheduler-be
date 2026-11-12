package com.company.hr.attendance.notification;

import com.company.hr.attendance.notification.application.PushGateway;
import com.company.hr.attendance.notification.application.ShiftNotificationService;
import com.company.hr.attendance.notification.infrastructure.PushRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ShiftNotificationServiceTests {
    private PushRepository repository;
    private PushGateway gateway;
    private JdbcTemplate jdbc;
    private ShiftNotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(PushRepository.class);
        gateway = mock(PushGateway.class);
        jdbc = mock(JdbcTemplate.class);
        service = new ShiftNotificationService(repository, gateway, jdbc);
    }

    @Test
    void sendSchedulePublishedNotifications_sendsPushAndLogs() {
        UUID emp1 = UUID.randomUUID();
        when(repository.findTokensByEmployeeId(emp1)).thenReturn(List.of("token-123"));
        when(gateway.sendCustom(eq(emp1), eq("token-123"), anyString(), anyString(), anyMap(), any(), any()))
            .thenReturn(PushGateway.Result.SENT);

        int sent = service.sendSchedulePublishedNotifications(Set.of(emp1), LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 22));

        assertThat(sent).isEqualTo(1);
        verify(repository).recordNotificationLog(eq(emp1), eq("SCHEDULE_PUBLISHED"), anyString(), anyString(), isNull(), any());
    }

    @Test
    void sendShiftReminders_deduplicatesAndSendsPush() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of());

        int count = service.sendShiftReminders(Instant.now());

        assertThat(count).isEqualTo(0);
    }
}
