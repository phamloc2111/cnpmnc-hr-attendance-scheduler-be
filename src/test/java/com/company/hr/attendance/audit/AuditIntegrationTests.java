package com.company.hr.attendance.audit;

import com.company.hr.attendance.audit.infrastructure.AuditLogRepository;
import com.company.hr.attendance.identity.domain.Account;
import com.company.hr.attendance.identity.domain.Role;
import com.company.hr.attendance.identity.infrastructure.AccountRepository;
import com.company.hr.attendance.identity.infrastructure.LoginSessionRepository;
import com.company.hr.attendance.organization.domain.Position;
import com.company.hr.attendance.organization.infrastructure.PositionRepository;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AuditApi auditApi;
    @Autowired AuditLogRepository auditRepo;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired PositionRepository positions;
    @Autowired ShiftTemplateRepository templates;
    @Autowired WorkScheduleRepository schedules;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    private static final String PASSWORD = "Test-password-123!";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private Account admin, manager1, manager2, empTeam1, empTeam2;
    private ShiftTemplate template;
    private String adminToken, manager1Token, manager2Token, emp1Token;

    @BeforeEach
    void setup() throws Exception {
        cleanupData();

        admin = accounts.save(new Account("admin.audit@example.com", passwords.encode(PASSWORD), "Quản trị viên", Role.ADMIN));
        manager1 = accounts.save(new Account("mgr1.audit@example.com", passwords.encode(PASSWORD), "Quản lý A", Role.MANAGER));
        manager2 = accounts.save(new Account("mgr2.audit@example.com", passwords.encode(PASSWORD), "Quản lý B", Role.MANAGER));

        Position pos = positions.save(new Position("Thu ngân", admin.getId()));
        empTeam1 = createEmployee("emp1.audit@example.com", "Nhân viên Một", pos, manager1.getId());
        empTeam2 = createEmployee("emp2.audit@example.com", "Nhân viên Hai", pos, manager2.getId());

        template = templates.save(new ShiftTemplate(
            "Ca sáng Audit", LocalTime.of(8, 0), LocalTime.of(12, 0), 0,
            admin.getId(), 1, pos.getName(), pos.getId(), false
        ));

        adminToken = login("admin.audit@example.com");
        manager1Token = login("mgr1.audit@example.com");
        manager2Token = login("mgr2.audit@example.com");
        emp1Token = login("emp1.audit@example.com");
    }

    @org.junit.jupiter.api.AfterEach
    void cleanupData() {
        auditRepo.deleteAll();
        schedules.deleteAll();
        templates.deleteAll();
        sessions.deleteAll();
        jdbc.update("DELETE FROM identity_account_skills");
        jdbc.update("UPDATE identity_accounts SET manager_id = NULL");
        accounts.deleteAll();
        positions.deleteAll();
    }

    private Account createEmployee(String email, String name, Position pos, UUID managerId) {
        var acc = new Account(email, passwords.encode(PASSWORD), name, Role.EMPLOYEE);
        acc.updateDetails(email, name, Role.EMPLOYEE, pos.getName(), List.of(), managerId);
        acc.setPositionId(pos.getId());
        return accounts.save(acc);
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
    void scheduleAssignAndCancel_writesAuditLogs_withFiltersAndRoleScoping() throws Exception {
        LocalDate workDate = LocalDate.now(VN).plusDays(2);

        // Manager 1 assigns shift to empTeam1 -> records SCHEDULE_ASSIGNED
        var assignRes = mvc.perform(post("/api/v1/work-schedules")
                .header("Authorization", "Bearer " + manager1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "employeeId", empTeam1.getId(),
                    "shiftTemplateId", template.getId(),
                    "workDate", workDate.toString()
                ))))
            .andExpect(status().isCreated())
            .andReturn();
        UUID scheduleId = UUID.fromString(mapper.readTree(assignRes.getResponse().getContentAsString()).get("id").asText());

        // Manager 1 cancels that shift -> records SCHEDULE_CANCELLED
        mvc.perform(delete("/api/v1/work-schedules/" + scheduleId)
                .header("Authorization", "Bearer " + manager1Token))
            .andExpect(status().isNoContent());

        // Manager 2 records an ATTENDANCE adjustment on empTeam2
        LocalDate otherDate = workDate.plusDays(1);
        auditApi.record(
            manager2.getId(),
            empTeam2.getId(),
            otherDate,
            "ATTENDANCE",
            "ATTENDANCE_ADJUSTED",
            "Điều chỉnh giờ công cho Nhân viên Hai",
            "0 phút",
            "240 phút",
            Instant.now()
        );

        // Employee cannot read audit logs
        mvc.perform(get("/api/v1/audit-logs")
                .header("Authorization", "Bearer " + emp1Token))
            .andExpect(status().isForbidden());

        // Manager 1 only sees Team 1 audit logs (2 records: cancel + assign), never Team 2
        mvc.perform(get("/api/v1/audit-logs")
                .header("Authorization", "Bearer " + manager1Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].action").value("SCHEDULE_CANCELLED"))
            .andExpect(jsonPath("$.items[0].actorName").value("Quản lý A"))
            .andExpect(jsonPath("$.items[0].targetEmployeeName").value("Nhân viên Một"))
            .andExpect(jsonPath("$.items[0].oldValue", containsString("Ca sáng Audit")))
            .andExpect(jsonPath("$.items[1].action").value("SCHEDULE_ASSIGNED"));

        // Manager 2 only sees Team 2 ATTENDANCE audit log (1 record)
        mvc.perform(get("/api/v1/audit-logs")
                .header("Authorization", "Bearer " + manager2Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].category").value("ATTENDANCE"))
            .andExpect(jsonPath("$.items[0].targetEmployeeId").value(empTeam2.getId().toString()));

        // Admin sees all 3 logs and can filter by category + date range
        mvc.perform(get("/api/v1/audit-logs")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(3));

        mvc.perform(get("/api/v1/audit-logs")
                .param("category", "ATTENDANCE")
                .param("from", otherDate.toString())
                .param("to", otherDate.toString())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].action").value("ATTENDANCE_ADJUSTED"));

        // Invalid date range returns 400
        mvc.perform(get("/api/v1/audit-logs")
                .param("from", otherDate.toString())
                .param("to", workDate.toString())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest());
    }
}
