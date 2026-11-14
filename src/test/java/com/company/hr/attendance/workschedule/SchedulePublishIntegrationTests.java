package com.company.hr.attendance.workschedule;

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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchedulePublishIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired PositionRepository positions;
    @Autowired ShiftTemplateRepository templates;
    @Autowired WorkScheduleRepository schedules;
    @Autowired PasswordEncoder passwords;

    private static final String PASSWORD = "Test-password-123!";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private Account manager, emp1, emp2;
    private ShiftTemplate shift8h, shift16h;
    private String managerToken, emp1Token;
    private LocalDate weekStart, weekEnd;

    @BeforeEach
    void setup() throws Exception {
        cleanup();

        manager = accounts.save(new Account("mgr.pub@example.com", passwords.encode(PASSWORD), "Quản lý Lịch", Role.MANAGER));
        Position pos = positions.save(new Position("Thu ngân", manager.getId()));

        emp1 = createEmployee("emp1.pub@example.com", "Nhân viên 1", pos, manager.getId());
        emp2 = createEmployee("emp2.pub@example.com", "Nhân viên 2", pos, manager.getId());

        shift8h = templates.save(new ShiftTemplate(
            "Ca 8h", LocalTime.of(8, 0), LocalTime.of(16, 0), 0,
            manager.getId(), 1, pos.getName(), pos.getId(), false
        ));
        shift16h = templates.save(new ShiftTemplate(
            "Ca dài 16h", LocalTime.of(6, 0), LocalTime.of(22, 0), 0,
            manager.getId(), 1, pos.getName(), pos.getId(), false
        ));

        weekStart = LocalDate.now(VN).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        weekEnd = weekStart.plusDays(6);

        managerToken = login("mgr.pub@example.com");
        emp1Token = login("emp1.pub@example.com");
    }

    @AfterEach
    void cleanup() {
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
    void reassignChecksHardOverlap_overtimeWarningRequiresConfirm_andOnlyPublishedVisibleToEmployee() throws Exception {
        // Assign draft shifts to emp1: Mon..Thu = 4 * 16h = 64h (> 48h cap) + Mon for emp2
        UUID monShiftId = assignAndExtractId(emp1.getId(), shift16h.getId(), weekStart);
        assignAndExtractId(emp1.getId(), shift16h.getId(), weekStart.plusDays(1));
        assignAndExtractId(emp1.getId(), shift16h.getId(), weekStart.plusDays(2));
        assignAndExtractId(emp1.getId(), shift8h.getId(), weekStart.plusDays(3)); // 56h total
        UUID emp2MonShiftId = assignAndExtractId(emp2.getId(), shift8h.getId(), weekStart);

        // Before publishing, manager sees 5 drafts but employee sees 0 schedules
        mvc.perform(get("/api/v1/work-schedules")
                .param("from", weekStart.toString())
                .param("to", weekEnd.toString())
                .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(5)))
            .andExpect(jsonPath("$.items[0].published").value(false));

        mvc.perform(get("/api/v1/work-schedules")
                .param("from", weekStart.toString())
                .param("to", weekEnd.toString())
                .header("Authorization", "Bearer " + emp1Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)));

        // Drag/reassign emp2's Mon 08:00-16:00 shift onto emp1 on Mon -> hard overlap 409 SCHEDULE_OVERLAP
        mvc.perform(put("/api/v1/work-schedules/" + emp2MonShiftId)
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "employeeId", emp1.getId(),
                    "shiftTemplateId", shift8h.getId(),
                    "workDate", weekStart.toString()
                ))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SCHEDULE_OVERLAP"));

        // Drag/reassign emp2's shift to Fri -> valid 200
        mvc.perform(put("/api/v1/work-schedules/" + emp2MonShiftId)
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "employeeId", emp2.getId(),
                    "shiftTemplateId", shift8h.getId(),
                    "workDate", weekStart.plusDays(4).toString()
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.workDate").value(weekStart.plusDays(4).toString()));

        // Publish without confirming overtime -> 400 OVERTIME_WARNING, shifts remain unpublished
        mvc.perform(post("/api/v1/work-schedules/publish")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "from", weekStart.toString(),
                    "to", weekEnd.toString(),
                    "confirmOvertime", false
                ))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("OVERTIME_WARNING"));

        mvc.perform(get("/api/v1/work-schedules")
                .param("from", weekStart.toString())
                .param("to", weekEnd.toString())
                .header("Authorization", "Bearer " + emp1Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)));

        // Publish WITH confirmOvertime=true -> 200 and all 4 shifts of emp1 become visible
        mvc.perform(post("/api/v1/work-schedules/publish")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "from", weekStart.toString(),
                    "to", weekEnd.toString(),
                    "confirmOvertime", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publishedCount").value(5));

        mvc.perform(get("/api/v1/work-schedules")
                .param("from", weekStart.toString())
                .param("to", weekEnd.toString())
                .header("Authorization", "Bearer " + emp1Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(4)))
            .andExpect(jsonPath("$.items[0].published").value(true));
    }

    private UUID assignAndExtractId(UUID employeeId, UUID shiftTemplateId, LocalDate workDate) throws Exception {
        var res = mvc.perform(post("/api/v1/work-schedules")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "employeeId", employeeId,
                    "shiftTemplateId", shiftTemplateId,
                    "workDate", workDate.toString()
                ))))
            .andExpect(status().isCreated())
            .andReturn();
        UUID id = UUID.fromString(mapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        jdbc.update("UPDATE work_schedules SET draft = true, published = false WHERE id = ?", id);
        return id;
    }
}
