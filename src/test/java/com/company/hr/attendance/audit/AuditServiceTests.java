package com.company.hr.attendance.audit;

import com.company.hr.attendance.audit.application.AuditService;
import com.company.hr.attendance.audit.domain.AuditLog;
import com.company.hr.attendance.audit.infrastructure.AuditLogRepository;
import com.company.hr.attendance.identity.IdentityApi;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditServiceTests {
    private AuditLogRepository repository;
    private IdentityApi identity;
    private AuditService service;

    @BeforeEach
    void setUp() {
        repository = mock(AuditLogRepository.class);
        identity = mock(IdentityApi.class);
        service = new AuditService(repository, identity);
    }

    @Test
    void record_savesAuditEntryWithActorTimestampOldAndNewValues() {
        UUID actorId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 20);
        Instant now = Instant.parse("2026-11-20T08:00:00Z");

        service.record(
            actorId,
            empId,
            date,
            "SCHEDULE",
            "SCHEDULE_ASSIGNED",
            "Phân ca Ca sáng",
            "Chưa có ca",
            "Ca sáng (08:00 - 12:00)",
            now
        );

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getTargetEmployeeId()).isEqualTo(empId);
        assertThat(saved.getTargetDate()).isEqualTo(date);
        assertThat(saved.getCategory()).isEqualTo("SCHEDULE");
        assertThat(saved.getAction()).isEqualTo("SCHEDULE_ASSIGNED");
        assertThat(saved.getOldValue()).isEqualTo("Chưa có ca");
        assertThat(saved.getNewValue()).isEqualTo("Ca sáng (08:00 - 12:00)");
        assertThat(saved.getOccurredAt()).isEqualTo(now);
    }

    @Test
    void list_returnsMappedAuditLogsForAdminAndRejectsEmployee() {
        UUID adminId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 11, 20);
        Instant now = Instant.parse("2026-11-20T09:00:00Z");

        when(identity.person(adminId)).thenReturn(new IdentityApi.Person(adminId, "Admin", "ADMIN", false, null));
        when(identity.person(empId)).thenReturn(new IdentityApi.Person(empId, "Employee", "EMPLOYEE", false, adminId));
        when(identity.names(any())).thenReturn(Map.of(adminId, "Admin", empId, "Nguyễn Văn A"));

        var log = new AuditLog(
            UUID.randomUUID(),
            adminId,
            empId,
            date,
            "ATTENDANCE",
            "DILIGENCE_REVIEW_UPDATED",
            "Cập nhật xác nhận chuyên cần",
            "Nghỉ: AUTO",
            "Nghỉ: APPROVED",
            now
        );
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(log)));

        var page = service.list(adminId, adminId, empId, "ATTENDANCE", date, date, 0, 20);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).actorName()).isEqualTo("Admin");
        assertThat(page.items().get(0).targetEmployeeName()).isEqualTo("Nguyễn Văn A");
        assertThat(page.items().get(0).oldValue()).isEqualTo("Nghỉ: AUTO");
        assertThat(page.items().get(0).newValue()).isEqualTo("Nghỉ: APPROVED");

        assertThatThrownBy(() -> service.list(empId, null, null, null, null, null, 0, 20))
            .isInstanceOf(ResponseStatusException.class);
    }
}
