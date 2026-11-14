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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShiftPassIntegrationTests {
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

    private Account manager, requester, colleagueSamePos, colleagueOtherPos;
    private Position cashierPos, kitchenPos;
    private WorkSchedule futureShift, pastShift;
    private String requesterToken, colleagueSameToken, colleagueOtherToken, managerToken;

    @BeforeEach
    void setup() throws Exception {
        passNotifs.deleteAll();
        passes.deleteAll();
        schedules.deleteAll();
        templates.deleteAll();
        sessions.deleteAll();
        jdbc.update("DELETE FROM identity_account_skills");
        accounts.deleteAll();
        positions.deleteAll();

        manager = new Account("mgr.pass@example.com", passwords.encode(PASSWORD), "Quản lý Minh", Role.MANAGER);
        accounts.save(manager);

        cashierPos = positions.save(new Position("Thu ngân", manager.getId()));
        kitchenPos = positions.save(new Position("Bếp", manager.getId()));

        requester = createEmployee("an.pass@example.com", "Nguyễn Văn An", cashierPos, manager.getId());
        colleagueSamePos = createEmployee("binh.pass@example.com", "Trần Bình", cashierPos, manager.getId());
        colleagueOtherPos = createEmployee("chi.pass@example.com", "Lê Chi", kitchenPos, manager.getId());

        var template = templates.save(new ShiftTemplate(
            "Ca sáng", LocalTime.of(8, 0), LocalTime.of(12, 0), 0,
            manager.getId(), 1, cashierPos.getName(), cashierPos.getId(), false
        ));

        futureShift = schedules.save(new WorkSchedule(
            requester.getId(), template, LocalDate.now(VN).plusDays(3), VN, manager.getId()
        ));
        pastShift = schedules.save(new WorkSchedule(
            requester.getId(), template, LocalDate.now(VN).minusDays(1), VN, manager.getId()
        ));

        requesterToken = login("an.pass@example.com");
        colleagueSameToken = login("binh.pass@example.com");
        colleagueOtherToken = login("chi.pass@example.com");
        managerToken = login("mgr.pass@example.com");
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
    void postPass_endToEnd_notifiesSamePositionOnlyAndKeepsOwnershipOnCancel() throws Exception {
        // Past shift rejected with 400
        mvc.perform(post("/api/v1/shift-passes")
                .header("Authorization", "Bearer " + requesterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("scheduleId", pastShift.getId()))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("code").value("SHIFT_ALREADY_STARTED"));

        // Colleague cannot post someone else's shift -> 403
        mvc.perform(post("/api/v1/shift-passes")
                .header("Authorization", "Bearer " + colleagueSameToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("scheduleId", futureShift.getId()))))
            .andExpect(status().isForbidden());

        // Manager cannot call employee-only POST -> 403
        mvc.perform(post("/api/v1/shift-passes")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("scheduleId", futureShift.getId()))))
            .andExpect(status().isForbidden());

        // Requester posts pass -> 201 Created, notifies only colleagueSamePos (notifiedCount = 1)
        var createRes = mvc.perform(post("/api/v1/shift-passes")
                .header("Authorization", "Bearer " + requesterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("scheduleId", futureShift.getId(), "reason", "Bận việc gia đình"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("status").value("OPEN"))
            .andExpect(jsonPath("notifiedCount").value(1))
            .andReturn();
        String passId = mapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText();

        // Duplicate open pass on same shift rejected -> 409 Conflict
        mvc.perform(post("/api/v1/shift-passes")
                .header("Authorization", "Bearer " + requesterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("scheduleId", futureShift.getId()))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("code").value("PASS_ALREADY_OPEN"));

        // Same-position colleague sees OPEN notification; other-position colleague sees empty
        mvc.perform(get("/api/v1/shift-passes/notifications")
                .header("Authorization", "Bearer " + colleagueSameToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("OPEN"));

        mvc.perform(get("/api/v1/shift-passes/notifications")
                .header("Authorization", "Bearer " + colleagueOtherToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        // Shift still belongs to requester
        assertThat(schedules.findById(futureShift.getId()).orElseThrow().getEmployeeId())
            .isEqualTo(requester.getId());

        // Cancel pass -> 204, notification auto-closed, ownership retained
        mvc.perform(delete("/api/v1/shift-passes/" + passId)
                .header("Authorization", "Bearer " + requesterToken))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/shift-passes/notifications")
                .header("Authorization", "Bearer " + colleagueSameToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("CLOSED"));

        assertThat(schedules.findById(futureShift.getId()).orElseThrow().getEmployeeId())
            .isEqualTo(requester.getId());

        // Re-posting same shift after cancel succeeds cleanly (multiple closed rows allowed)
        mvc.perform(post("/api/v1/shift-passes")
                .header("Authorization", "Bearer " + requesterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("scheduleId", futureShift.getId(), "reason", "Đăng lại"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("status").value("OPEN"));
    }
}
