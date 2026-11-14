package com.company.hr.attendance.shiftpass;

import com.company.hr.attendance.identity.domain.Account;
import com.company.hr.attendance.identity.domain.Role;
import com.company.hr.attendance.identity.infrastructure.AccountRepository;
import com.company.hr.attendance.identity.infrastructure.LoginSessionRepository;
import com.company.hr.attendance.organization.domain.Position;
import com.company.hr.attendance.organization.infrastructure.PositionRepository;
import com.company.hr.attendance.shiftpass.infrastructure.ShiftPassNotificationRepository;
import com.company.hr.attendance.shiftpass.infrastructure.ShiftPassRepository;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShiftPassClaimIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired PositionRepository positions;
    @Autowired ShiftTemplateRepository templates;
    @Autowired WorkScheduleRepository schedules;
    @Autowired ShiftPassRepository passes;
    @Autowired ShiftPassNotificationRepository passNotifs;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    private static final String PASSWORD = "Test-password-123!";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private Account manager, requester, claimer1, claimer2, overlapEmp, overCapEmp, otherPosEmp;
    private WorkSchedule passShift;
    private String requesterToken, claimer1Token, claimer2Token, overlapToken, overCapToken, otherPosToken, managerToken;

    @BeforeEach
    void setup() throws Exception {
        jdbc.update("DELETE FROM audit_logs");
        passNotifs.deleteAll();
        passes.deleteAll();
        schedules.deleteAll();
        templates.deleteAll();
        sessions.deleteAll();
        jdbc.update("DELETE FROM identity_account_skills");
        jdbc.update("UPDATE identity_accounts SET manager_id = NULL");
        accounts.deleteAll();
        positions.deleteAll();

        manager = accounts.save(new Account("mgr.claim@example.com", passwords.encode(PASSWORD), "Quản lý Lan", Role.MANAGER));
        Position cashierPos = positions.save(new Position("Thu ngân", manager.getId()));
        Position kitchenPos = positions.save(new Position("Bếp", manager.getId()));

        requester = createEmployee("an.claim@example.com", "Nguyễn Văn An", cashierPos, manager.getId());
        claimer1 = createEmployee("binh.claim@example.com", "Trần Bình", cashierPos, manager.getId());
        claimer2 = createEmployee("dung.claim@example.com", "Phạm Dũng", cashierPos, manager.getId());
        overlapEmp = createEmployee("trung.claim@example.com", "Hoàng Trung", cashierPos, manager.getId());
        overCapEmp = createEmployee("vuot.claim@example.com", "Đặng Vượt", cashierPos, manager.getId());
        otherPosEmp = createEmployee("bep.claim@example.com", "Lê Bếp", kitchenPos, manager.getId());

        LocalDate nextMon = LocalDate.now(VN).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        LocalDate passDate = nextMon.plusDays(2); // Wednesday of next week

        var morningShift = templates.save(new ShiftTemplate(
            "Ca sáng 4h", LocalTime.of(8, 0), LocalTime.of(12, 0), 0,
            manager.getId(), 2, cashierPos.getName(), cashierPos.getId(), false
        ));
        var overlappingShift = templates.save(new ShiftTemplate(
            "Ca trùng 4h", LocalTime.of(9, 0), LocalTime.of(13, 0), 0,
            manager.getId(), 1, cashierPos.getName(), cashierPos.getId(), false
        ));
        // 15.5h shift across 3 non-Wednesday days = 46.5h/week (>44h so +4h > 48h cap)
        var longDayShift = templates.save(new ShiftTemplate(
            "Ca dài", LocalTime.of(6, 0), LocalTime.of(21, 30), 0,
            manager.getId(), 1, cashierPos.getName(), cashierPos.getId(), false
        ));

        passShift = schedules.save(new WorkSchedule(requester.getId(), morningShift, passDate, VN, manager.getId()));
        schedules.save(new WorkSchedule(overlapEmp.getId(), overlappingShift, passDate, VN, manager.getId()));
        schedules.save(new WorkSchedule(overCapEmp.getId(), longDayShift, nextMon, VN, manager.getId()));
        schedules.save(new WorkSchedule(overCapEmp.getId(), longDayShift, nextMon.plusDays(1), VN, manager.getId()));
        schedules.save(new WorkSchedule(overCapEmp.getId(), longDayShift, nextMon.plusDays(3), VN, manager.getId()));

        requesterToken = login("an.claim@example.com");
        claimer1Token = login("binh.claim@example.com");
        claimer2Token = login("dung.claim@example.com");
        overlapToken = login("trung.claim@example.com");
        overCapToken = login("vuot.claim@example.com");
        otherPosToken = login("bep.claim@example.com");
        managerToken = login("mgr.claim@example.com");
    }

    private Account createEmployee(String email, String name, Position pos, UUID managerId) {
        var a = new Account(email, passwords.encode(PASSWORD), name, Role.EMPLOYEE);
        a.updateDetails(email, name, Role.EMPLOYEE, pos.getName(), List.of(), managerId);
        a.setPositionId(pos.getId());
        return accounts.save(a);
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
    void availableEligibility_claimTransfer_raceConflict_notificationsAndAuditLogEndToEnd() throws Exception {
        var created = mvc.perform(post("/api/v1/shift-passes")
                .header("Authorization", "Bearer " + requesterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("scheduleId", passShift.getId(), "reason", "Bận đột xuất"))))
            .andExpect(status().isCreated())
            .andReturn();
        UUID passId = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString()).get("id").asText());

        // Same-position eligible claimer sees eligible = true
        mvc.perform(get("/api/v1/shift-passes/available")
                .header("Authorization", "Bearer " + claimer1Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(passId.toString()))
            .andExpect(jsonPath("$[0].eligible").value(true));

        // Overlapping colleague sees eligible = false & gets 409 on direct claim
        mvc.perform(get("/api/v1/shift-passes/available")
                .header("Authorization", "Bearer " + overlapToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eligible").value(false))
            .andExpect(jsonPath("$[0].ineligibleReason", containsString("Trùng giờ")));

        mvc.perform(post("/api/v1/shift-passes/" + passId + "/claim")
                .header("Authorization", "Bearer " + overlapToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SCHEDULE_OVERLAP"));

        // >48h/week colleague sees eligible = false & gets 409 on direct claim
        mvc.perform(get("/api/v1/shift-passes/available")
                .header("Authorization", "Bearer " + overCapToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eligible").value(false))
            .andExpect(jsonPath("$[0].ineligibleReason", containsString("48 giờ/tuần")));

        mvc.perform(post("/api/v1/shift-passes/" + passId + "/claim")
                .header("Authorization", "Bearer " + overCapToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("WEEKLY_HOUR_LIMIT_EXCEEDED"));

        // Different position employee does not see pass & gets 403 on direct claim
        mvc.perform(get("/api/v1/shift-passes/available")
                .header("Authorization", "Bearer " + otherPosToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/v1/shift-passes/" + passId + "/claim")
                .header("Authorization", "Bearer " + otherPosToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("POSITION_MISMATCH"));

        // First eligible claimer wins -> 200 CLAIMED & work_schedules transfers ownership
        mvc.perform(post("/api/v1/shift-passes/" + passId + "/claim")
                .header("Authorization", "Bearer " + claimer1Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLAIMED"))
            .andExpect(jsonPath("$.claimerId").value(claimer1.getId().toString()))
            .andExpect(jsonPath("$.claimerName").value("Trần Bình"));

        var updatedSchedule = schedules.findById(passShift.getId()).orElseThrow();
        assertThat(updatedSchedule.getEmployeeId()).isEqualTo(claimer1.getId());

        // Second concurrent claimer fails with 409 PASS_ALREADY_CLOSED
        mvc.perform(post("/api/v1/shift-passes/" + passId + "/claim")
                .header("Authorization", "Bearer " + claimer2Token))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PASS_ALREADY_CLOSED"));

        // All PASS_OFFERED notifications are closed; claimer2 sees none open
        var openOffers = passNotifs.findAll().stream()
            .filter(n -> "PASS_OFFERED".equals(n.getKind()) && "OPEN".equals(n.getStatus()))
            .toList();
        assertThat(openOffers).isEmpty();

        // Requester receives PASS_CLAIMED_OWNER, manager receives PASS_CLAIMED_MANAGER
        mvc.perform(get("/api/v1/shift-passes/notifications")
                .header("Authorization", "Bearer " + requesterToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].kind").value("PASS_CLAIMED_OWNER"))
            .andExpect(jsonPath("$[0].status").value("OPEN"));

        mvc.perform(get("/api/v1/shift-passes/notifications")
                .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].kind").value("PASS_CLAIMED_MANAGER"))
            .andExpect(jsonPath("$[0].body", containsString("Trần Bình")));

        // Audit log row is persisted cleanly in DB
        var auditRows = jdbc.query(
            "SELECT actor_id, target_employee_id, category FROM audit_logs WHERE action = 'SHIFT_PASS_CLAIMED'",
            (rs, rowNum) -> List.of(rs.getString("actor_id"), rs.getString("target_employee_id"), rs.getString("category"))
        );
        assertThat(auditRows).hasSize(1);
        assertThat(auditRows.get(0)).containsExactly(
            claimer1.getId().toString(),
            requester.getId().toString(),
            "SCHEDULE"
        );
    }
}
