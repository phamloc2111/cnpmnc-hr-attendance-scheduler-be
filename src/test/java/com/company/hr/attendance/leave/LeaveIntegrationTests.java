package com.company.hr.attendance.leave;

import com.company.hr.attendance.identity.domain.Account;
import com.company.hr.attendance.identity.domain.Role;
import com.company.hr.attendance.identity.infrastructure.AccountRepository;
import com.company.hr.attendance.identity.infrastructure.LoginSessionRepository;
import com.company.hr.attendance.leave.infrastructure.LeaveRequestRepository;
import com.company.hr.attendance.organization.domain.Position;
import com.company.hr.attendance.organization.infrastructure.PositionRepository;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaveIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired PositionRepository positions;
    @Autowired ShiftTemplateRepository templates;
    @Autowired WorkScheduleRepository schedules;
    @Autowired LeaveRequestRepository leaves;
    @Autowired PasswordEncoder passwords;

    private static final String PASSWORD = "Test-password-123!";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private Account manager1, manager2, emp;
    private WorkSchedule shift1, shift2;
    private LocalDate leaveDate1, leaveDate2, leaveDate3;
    private YearMonth targetMonth;
    private String mgr1Token, mgr2Token, empToken;

    @BeforeEach
    void setup() throws Exception {
        cleanup();

        manager1 = accounts.save(new Account("mgr1.leave@example.com", passwords.encode(PASSWORD), "Quản lý Chính", Role.MANAGER));
        manager2 = accounts.save(new Account("mgr2.leave@example.com", passwords.encode(PASSWORD), "Quản lý Khác", Role.MANAGER));
        Position pos = positions.save(new Position("Thu ngân", manager1.getId()));

        emp = new Account("emp.leave@example.com", passwords.encode(PASSWORD), "Nhân viên Xin Nghỉ", Role.EMPLOYEE);
        emp.updateDetails("emp.leave@example.com", "Nhân viên Xin Nghỉ", Role.EMPLOYEE, pos.getName(), List.of(), manager1.getId());
        emp.setPositionId(pos.getId());
        emp = accounts.save(emp);

        // Use a fixed next month to guarantee 3 distinct valid dates inside one calendar month
        LocalDate nextMonthFirst = LocalDate.now(VN).plusMonths(1).withDayOfMonth(1);
        targetMonth = YearMonth.from(nextMonthFirst);
        leaveDate1 = nextMonthFirst.plusDays(1);
        leaveDate2 = nextMonthFirst.plusDays(2);
        leaveDate3 = nextMonthFirst.plusDays(3);

        var tpl = templates.save(new ShiftTemplate(
            "Ca sáng", LocalTime.of(8, 0), LocalTime.of(12, 0), 0,
            manager1.getId(), 1, pos.getName(), pos.getId(), false
        ));
        shift1 = schedules.save(new WorkSchedule(emp.getId(), tpl, leaveDate1, VN, manager1.getId()));
        shift2 = schedules.save(new WorkSchedule(emp.getId(), tpl, leaveDate2, VN, manager1.getId()));

        mgr1Token = login("mgr1.leave@example.com");
        mgr2Token = login("mgr2.leave@example.com");
        empToken = login("emp.leave@example.com");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM diligence_day_reviews");
        leaves.deleteAll();
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
    void leaveQuotaSubmissionReviewUnderstaffedAndDiligenceExemption_endToEnd() throws Exception {
        // Initial quota = 2 remaining
        mvc.perform(get("/api/v1/leave-requests/quota")
                .param("month", targetMonth.toString())
                .header("Authorization", "Bearer " + empToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.remaining").value(2))
            .andExpect(jsonPath("$.canSubmit").value(true));

        // Invalid month format returns 400 instead of 500
        mvc.perform(get("/api/v1/leave-requests/quota")
                .param("month", "not-a-month")
                .header("Authorization", "Bearer " + empToken))
            .andExpect(status().isBadRequest());

        // Submit leave #1 for shift1 -> PENDING
        var res1 = mvc.perform(post("/api/v1/leave-requests")
                .header("Authorization", "Bearer " + empToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "scheduleId", shift1.getId(),
                    "workDate", leaveDate1.toString(),
                    "reason", "Khám sức khỏe định kỳ"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn();
        UUID leaveId1 = UUID.fromString(mapper.readTree(res1.getResponse().getContentAsString()).get("id").asText());

        // Duplicate leave for same schedule blocked with 400
        mvc.perform(post("/api/v1/leave-requests")
                .header("Authorization", "Bearer " + empToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "scheduleId", shift1.getId(),
                    "workDate", leaveDate1.toString(),
                    "reason", "Gửi trùng"
                ))))
            .andExpect(status().isBadRequest());

        // Submit leave #2 for shift2 -> PENDING (quota drops to 0)
        var res2 = mvc.perform(post("/api/v1/leave-requests")
                .header("Authorization", "Bearer " + empToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "scheduleId", shift2.getId(),
                    "workDate", leaveDate2.toString(),
                    "reason", "Việc gia đình"
                ))))
            .andExpect(status().isOk())
            .andReturn();
        UUID leaveId2 = UUID.fromString(mapper.readTree(res2.getResponse().getContentAsString()).get("id").asText());

        // 3rd leave in same month blocked with 400
        mvc.perform(post("/api/v1/leave-requests")
                .header("Authorization", "Bearer " + empToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of(
                    "workDate", leaveDate3.toString(),
                    "reason", "Vượt 2 lần/tháng"
                ))))
            .andExpect(status().isBadRequest());

        // Outside manager cannot list or review emp's requests
        mvc.perform(get("/api/v1/leave-requests")
                .header("Authorization", "Bearer " + mgr2Token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(0)));

        mvc.perform(put("/api/v1/leave-requests/" + leaveId1 + "/review")
                .header("Authorization", "Bearer " + mgr2Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("action", "APPROVE", "note", "Không thuộc quyền"))))
            .andExpect(status().isForbidden());

        // Team manager rejects leave #2 -> quota restored to 1 remaining, shift2 stays not understaffed
        mvc.perform(put("/api/v1/leave-requests/" + leaveId2 + "/review")
                .header("Authorization", "Bearer " + mgr1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("action", "REJECT", "note", "Thiếu người"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));

        assertThat(schedules.findById(shift2.getId()).orElseThrow().isUnderstaffed()).isFalse();

        mvc.perform(get("/api/v1/leave-requests/quota")
                .param("month", targetMonth.toString())
                .header("Authorization", "Bearer " + empToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pending").value(1))
            .andExpect(jsonPath("$.remaining").value(1))
            .andExpect(jsonPath("$.canSubmit").value(true));

        // Team manager approves leave #1 -> APPROVED, shift1 becomes understaffed=true, diligence_day_reviews marked APPROVED_LEAVE
        mvc.perform(put("/api/v1/leave-requests/" + leaveId1 + "/review")
                .header("Authorization", "Bearer " + mgr1Token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("action", "APPROVE", "note", "Chấp nhận"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(schedules.findById(shift1.getId()).orElseThrow().isUnderstaffed()).isTrue();
        var reviewAbsence = jdbc.queryForObject(
            "SELECT absence FROM diligence_day_reviews WHERE employee_id = ? AND work_date = ?",
            String.class,
            emp.getId(),
            java.sql.Date.valueOf(leaveDate1)
        );
        assertThat(reviewAbsence).isEqualTo("APPROVED_LEAVE");
    }
}
