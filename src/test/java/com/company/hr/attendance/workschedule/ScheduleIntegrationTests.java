package com.company.hr.attendance.workschedule;

import com.company.hr.attendance.identity.PostgresTestConfig;
import com.company.hr.attendance.identity.domain.*;
import com.company.hr.attendance.identity.infrastructure.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class ScheduleIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    Account admin, manager, otherManager, employee, outsider;
    String adminToken, managerToken, employeeToken;
    static final String PASSWORD = "Test-password-123!";

    @BeforeEach void seed() throws Exception {
        jdbc.update("DELETE FROM work_schedules");
        jdbc.update("DELETE FROM work_shift_templates");
        sessions.deleteAll();
        jdbc.update("DELETE FROM identity_account_skills");
        jdbc.update("DELETE FROM identity_accounts");
        admin = save("admin", Role.ADMIN, null);
        manager = save("manager", Role.MANAGER, null);
        otherManager = save("other-manager", Role.MANAGER, null);
        employee = save("employee", Role.EMPLOYEE, manager.getId());
        outsider = save("outsider", Role.EMPLOYEE, otherManager.getId());
        adminToken = token(admin); managerToken = token(manager); employeeToken = token(employee);
    }
    Account save(String name, Role role, UUID managerId) {
        var account = new Account(name + "@example.com", passwords.encode(PASSWORD), name, role);
        account.updateDetails(account.getEmail(), name, role, null, List.of(), managerId);
        return accounts.saveAndFlush(account);
    }
    String token(Account account) throws Exception {
        return json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", account.getEmail(), "password", PASSWORD))))
            .andExpect(status().isOk())).get("accessToken").asText();
    }
    JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
    Map<String, Object> shiftBody(String start, String end, int rest) {
        return Map.of("name", "Ca làm", "startTime", start, "endTime", end, "breakMinutes", rest);
    }
    JsonNode shift(String start, String end, int rest) throws Exception {
        return json(mvc.perform(post("/api/v1/shift-templates").header("Authorization", "Bearer " + managerToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(shiftBody(start, end, rest))))
            .andExpect(status().isCreated()));
    }
    ResultActions assign(String token, Account target, JsonNode shift, String date) throws Exception {
        return mvc.perform(post("/api/v1/work-schedules").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                "employeeId", target.getId(), "shiftTemplateId", shift.get("id").asText(), "workDate", date))));
    }
    ResultActions list(String token) throws Exception {
        return mvc.perform(get("/api/v1/work-schedules").header("Authorization", "Bearer " + token)
            .param("from", "2026-11-02").param("to", "2026-11-08"));
    }
    @Test void migration_postgres_appliesV4() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL");
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '4' and success", Integer.class)).isEqualTo(1);
    }
    @Test void createShift_overnight_returnsWorkingMinutes() throws Exception {
        var result = shift("22:00", "06:00", 30);
        assertThat(result.get("overnight").asBoolean()).isTrue();
        assertThat(result.get("workingMinutes").asInt()).isEqualTo(450);
    }
    @ParameterizedTest
    @CsvSource({"08:00,08:00,0", "08:00,09:00,60", "22:00,06:00,480", "08:00:30,09:00,0", "08:00,09:00,-1"})
    void createShift_invalidDuration_returns400(String start, String end, int rest) throws Exception {
        mvc.perform(post("/api/v1/shift-templates").header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(shiftBody(start, end, rest))))
            .andExpect(status().isBadRequest());
    }
    @Test void createShift_employee_returns403() throws Exception {
        mvc.perform(post("/api/v1/shift-templates").header("Authorization", "Bearer " + employeeToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(shiftBody("08:00", "17:00", 60))))
            .andExpect(status().isForbidden());
    }
    @Test void list_anonymous_returns401() throws Exception {
        mvc.perform(get("/api/v1/work-schedules").param("from", "2026-11-02").param("to", "2026-11-08"))
            .andExpect(status().isUnauthorized());
    }
    @Test void assign_dayShift_persistsUtcAndNetMinutes() throws Exception {
        var result = json(assign(managerToken, employee, shift("08:00", "17:00", 60), "2026-11-05")
            .andExpect(status().isCreated()).andExpect(header().exists("Location")));
        assertThat(result.get("startsAt").asText()).isEqualTo("2026-11-05T01:00:00Z");
        assertThat(result.get("endsAt").asText()).isEqualTo("2026-11-05T10:00:00Z");
        assertThat(result.get("workDate").asText()).isEqualTo("2026-11-05");
        assertThat(result.get("workingMinutes").asInt()).isEqualTo(480);
        assertThat(result.get("timezone").asText()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(jdbc.queryForObject("select count(*) from work_schedules", Integer.class)).isEqualTo(1);
    }
    @Test void assign_overnightConflictsWithNextDay_returns409() throws Exception {
        var night = shift("22:00", "06:00", 0);
        assign(managerToken, employee, night, "2026-11-04").andExpect(status().isCreated());
        assign(managerToken, employee, shift("05:00", "09:00", 0), "2026-11-05")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCHEDULE_OVERLAP"));
        // A shift beginning exactly at the previous shift's end is allowed.
        assign(managerToken, employee, shift("06:00", "10:00", 0), "2026-11-05").andExpect(status().isCreated());
        assign(managerToken, employee, night, "2026-11-04").andExpect(status().isConflict());
    }
    @Test void assign_previousDayNightConflictsWithExistingMorning_returns409() throws Exception {
        assign(managerToken, employee, shift("05:00", "09:00", 0), "2026-11-05").andExpect(status().isCreated());
        assign(managerToken, employee, shift("22:00", "06:00", 0), "2026-11-04").andExpect(status().isConflict());
    }
    @Test void assign_managerCannotAssignOutsideTeamOrManagement_returns404() throws Exception {
        var day = shift("08:00", "17:00", 0);
        assign(managerToken, outsider, day, "2026-11-05").andExpect(status().isNotFound());
        assign(adminToken, manager, day, "2026-11-05").andExpect(status().isNotFound());
        assign(employeeToken, employee, day, "2026-11-05").andExpect(status().isForbidden());
    }
    @Test void assign_lockedEmployee_returns409() throws Exception {
        employee.setLocked(true); accounts.saveAndFlush(employee);
        assign(managerToken, employee, shift("08:00", "17:00", 0), "2026-11-05")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMPLOYEE_LOCKED"));
    }
    @Test void list_roles_filterRowsAndPreventEmployeeOverride() throws Exception {
        var day = shift("08:00", "17:00", 0);
        assign(adminToken, employee, day, "2026-11-05").andExpect(status().isCreated());
        assign(adminToken, outsider, day, "2026-11-05").andExpect(status().isCreated());
        list(adminToken).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        list(managerToken).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].employeeId").value(employee.getId().toString()));
        list(employeeToken).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].employeeId").value(employee.getId().toString()));
        mvc.perform(get("/api/v1/work-schedules").header("Authorization", "Bearer " + employeeToken)
            .param("from", "2026-11-02").param("to", "2026-11-08").param("employeeId", outsider.getId().toString()))
            .andExpect(status().isForbidden());
    }
    @Test void list_dateBoundariesAndPagination_stableAndInclusive() throws Exception {
        var day = shift("08:00", "17:00", 0);
        for (String date : List.of("2026-11-01", "2026-11-02", "2026-11-08", "2026-11-09"))
            assign(adminToken, employee, day, date).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/work-schedules").header("Authorization", "Bearer " + adminToken)
            .param("from", "2026-11-02").param("to", "2026-11-08").param("size", "1").param("page", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.items[0].workDate").value("2026-11-08"));
    }
    @ParameterizedTest
    @CsvSource({"2026-11-08,2026-11-02", "2026-11-01,2026-12-02", "wrong,2026-11-05"})
    void list_invalidDates_returns400(String from, String to) throws Exception {
        mvc.perform(get("/api/v1/work-schedules").header("Authorization", "Bearer " + managerToken)
            .param("from", from).param("to", to)).andExpect(status().isBadRequest());
    }
    @Test void cancel_scopeCheckedAndCancelledSlotReusable() throws Exception {
        var day = shift("08:00", "17:00", 0);
        var own = json(assign(adminToken, employee, day, "2026-11-05").andExpect(status().isCreated()));
        var other = json(assign(adminToken, outsider, day, "2026-11-05").andExpect(status().isCreated()));
        mvc.perform(delete("/api/v1/work-schedules/" + other.get("id").asText()).header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/work-schedules/" + own.get("id").asText()).header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/work-schedules/" + own.get("id").asText()).header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isNoContent());
        assign(managerToken, employee, day, "2026-11-05").andExpect(status().isCreated());
    }
    @Test void assign_concurrentOverlappingRequests_onlyOneSucceeds() throws Exception {
        var day = shift("08:00", "17:00", 0);
        var overlapping = shift("09:00", "18:00", 0);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var futures = new ArrayList<Future<Integer>>();
            for (var template : List.of(day, overlapping)) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                    return assign(managerToken, employee, template, "2026-11-05").andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("select count(*) from work_schedules", Integer.class)).isEqualTo(1);
        }
    }
}
