package com.company.hr.attendance.workschedule;

import com.company.hr.attendance.identity.PostgresTestConfig;
import com.company.hr.attendance.identity.domain.*;
import com.company.hr.attendance.identity.infrastructure.*;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.flywaydb.core.Flyway;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.scheduling.max-weekly-minutes=2400")
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
    @Autowired DataSource dataSource;
    @Autowired WorkScheduleApi scheduleDirectory;
    @Autowired TransactionTemplate transactions;
    Account admin, manager, otherManager, employee, outsider;
    String adminToken, managerToken, employeeToken;
    static final UUID POSITION_ID = UUID.fromString("00000000-0000-0000-0000-000000000051");
    static final String PASSWORD = "Test-password-123!";

    @BeforeEach void seed() throws Exception {
        jdbc.update("DELETE FROM schedule_draft_weeks");
        jdbc.update("DELETE FROM work_schedules");
        jdbc.update("DELETE FROM work_shift_templates");
        sessions.deleteAll();
        jdbc.update("DELETE FROM identity_account_skills");
        jdbc.update("DELETE FROM identity_accounts");
        jdbc.update("DELETE FROM organization_positions");
        jdbc.update("insert into organization_positions(id,name,normalized_name,active,version) values (?,?,?,true,0)", POSITION_ID, "Thu ngân", "thu ngân");
        admin = save("admin", Role.ADMIN, null);
        manager = save("manager", Role.MANAGER, null);
        otherManager = save("other-manager", Role.MANAGER, null);
        employee = save("employee", Role.EMPLOYEE, manager.getId());
        outsider = save("outsider", Role.EMPLOYEE, otherManager.getId());
        adminToken = token(admin); managerToken = token(manager); employeeToken = token(employee);
    }

    @Test void list_employeeHome_exposesAssignedShiftPositionWithoutCatalogAccess() throws Exception {
        var template = shift("07:00", "11:00", 0);
        assign(managerToken, employee, template, "2026-11-09").andExpect(status().isCreated())
            .andExpect(jsonPath("$.requiredPosition").value("Thu ngân"));
        assign(adminToken, outsider, template, "2026-11-09").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/work-schedules").header("Authorization", "Bearer " + employeeToken)
            .param("from", "2026-11-08").param("to", "2026-11-09"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].requiredPosition").value("Thu ngân"))
            .andExpect(jsonPath("$.items[0].startsAt").value("2026-11-09T00:00:00Z"));
    }

    @Test void draftAssignmentIsVisibleToManagerButNotEmployeeOrAttendance() throws Exception {
        var template = shift("07:00", "11:00", 0);
        var created = json(mvc.perform(post("/api/v1/work-schedules/draft-assignments")
            .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("employeeId", employee.getId(),
                "shiftTemplateId", template.get("id").asText(), "workDate", "2026-11-05"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.draft").value(true)));
        mvc.perform(get("/api/v1/work-schedules").header("Authorization", "Bearer " + managerToken)
            .param("from", "2026-11-02").param("to", "2026-11-08"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].draft").value(true));
        mvc.perform(get("/api/v1/work-schedules").header("Authorization", "Bearer " + employeeToken)
            .param("from", "2026-11-02").param("to", "2026-11-08"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        var locked = transactions.execute(ignored -> scheduleDirectory.lock(
            UUID.fromString(created.get("id").asText())));
        assertThat(locked.isEmpty()).isTrue();
        assertThat(scheduleDirectory.employeeShifts(List.of(employee.getId()),
            java.time.LocalDate.parse("2026-11-02"), java.time.LocalDate.parse("2026-11-08"))).isEmpty();
        mvc.perform(get("/api/v1/work-schedules/draft-warnings")
            .header("Authorization", "Bearer " + managerToken).param("weekStart", "2026-11-02"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.draftExists").value(true))
            .andExpect(jsonPath("$.missingCount").value(6));
    }

    @Test void oneClickDraftReportsShortagesWhenNobodyRegistered() throws Exception {
        var template = shift("07:00", "11:00", 0);
        mvc.perform(post("/api/v1/work-schedules/drafts")
            .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"weekStart\":\"2026-11-16\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.missingCount").value(7))
            .andExpect(jsonPath("$.shortages.length()").value(7));
        assertThat(jdbc.queryForObject("select count(*) from work_schedules", Integer.class)).isZero();
        mvc.perform(get("/api/v1/work-schedules/draft-warnings")
            .header("Authorization", "Bearer " + managerToken).param("weekStart", "2026-11-16"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.draftExists").value(true))
            .andExpect(jsonPath("$.missingCount").value(7))
            .andExpect(jsonPath("$.shortages[0].shiftTemplateId").value(template.get("id").asText()));
        mvc.perform(get("/api/v1/work-schedules/draft-warnings")
            .header("Authorization", "Bearer " + employeeToken).param("weekStart", "2026-11-16"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/work-schedules/generate")
            .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test void oneClickDraftSkipsTemplatesWhosePositionWasDeactivated() throws Exception {
        shift("07:00", "11:00", 0);
        jdbc.update("UPDATE organization_positions SET active=false WHERE id=?", POSITION_ID);

        mvc.perform(post("/api/v1/work-schedules/drafts")
            .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"weekStart\":\"2026-11-16\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.missingCount").value(0));
        mvc.perform(get("/api/v1/work-schedules/draft-warnings")
            .header("Authorization", "Bearer " + managerToken).param("weekStart", "2026-11-16"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.draftExists").value(true))
            .andExpect(jsonPath("$.missingCount").value(0));

        var activePosition = UUID.randomUUID();
        jdbc.update("insert into organization_positions(id,name,normalized_name,active,version) values (?,?,?,true,0)",
            activePosition, "Phục vụ", "phục vụ");
        var activeShift = new HashMap<>(shiftBody("12:00", "16:00", 0));
        activeShift.put("requiredPositionId", activePosition);
        mvc.perform(post("/api/v1/shift-templates").header("Authorization", "Bearer " + managerToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(activeShift)))
            .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/work-schedules/drafts")
            .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"weekStart\":\"2026-11-16\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.missingCount").value(7));
        mvc.perform(get("/api/v1/work-schedules/draft-warnings")
            .header("Authorization", "Bearer " + managerToken).param("weekStart", "2026-11-16"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.missingCount").value(7));
    }

    @Test void oneClickDraftSkipsLegacyTemplateWithoutPositionId() throws Exception {
        var legacyShift = shift("07:00", "11:00", 0);
        jdbc.update("UPDATE work_shift_templates SET required_position_id=NULL WHERE id=?",
            UUID.fromString(legacyShift.get("id").asText()));

        mvc.perform(post("/api/v1/work-schedules/drafts")
            .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"weekStart\":\"2026-11-16\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(0))
            .andExpect(jsonPath("$.missingCount").value(0));
        mvc.perform(get("/api/v1/work-schedules/draft-warnings")
            .header("Authorization", "Bearer " + managerToken).param("weekStart", "2026-11-16"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.missingCount").value(0));
    }

    @Test void draftWarningsRecalculateShortagesAndOvertimeAfterManualAssignment() throws Exception {
        var template = shift("07:00", "22:00", 0);
        mvc.perform(post("/api/v1/work-schedules/drafts")
            .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"weekStart\":\"2026-11-16\"}"))
            .andExpect(status().isOk());
        for (int day = 16; day <= 18; day++) {
            mvc.perform(post("/api/v1/work-schedules/draft-assignments")
                .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("employeeId", employee.getId(),
                    "shiftTemplateId", template.get("id").asText(), "workDate", "2026-11-" + day))))
                .andExpect(status().isCreated());
        }
        mvc.perform(get("/api/v1/work-schedules/draft-warnings")
            .header("Authorization", "Bearer " + managerToken).param("weekStart", "2026-11-16"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.missingCount").value(4))
            .andExpect(jsonPath("$.overtime[0].employeeId").value(employee.getId().toString()))
            .andExpect(jsonPath("$.overtime[0].weeklyMinutes").value(2700))
            .andExpect(jsonPath("$.overtime[0].exceededMinutes").value(300));
        mvc.perform(get("/api/v1/work-schedules/draft-warnings")
            .header("Authorization", "Bearer " + token(otherManager)).param("weekStart", "2026-11-16"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.draftExists").value(false))
            .andExpect(jsonPath("$.overtime.length()").value(0));
    }

    @Test void list_legacyShiftWithoutPosition_returnsNullablePosition() throws Exception {
        var template = shift("07:00", "11:00", 0);
        jdbc.update("update work_shift_templates set required_position=null where id=?", UUID.fromString(template.get("id").asText()));
        assign(managerToken, employee, template, "2026-11-09").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/work-schedules").header("Authorization", "Bearer " + employeeToken)
            .param("from", "2026-11-09").param("to", "2026-11-09"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].requiredPosition").isEmpty());
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
        return Map.of("name", "Ca làm", "startTime", start, "endTime", end, "breakMinutes", rest,
            "requiredHeadcount", 1, "requiredPositionId", POSITION_ID,
            "overnight", java.time.LocalTime.parse(end).isBefore(java.time.LocalTime.parse(start)));
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
    @Test void createShift_staffingRequirements_persistAndAppearInList() throws Exception {
        var body = new HashMap<>(shiftBody("08:00", "17:00", 60));
        body.put("requiredHeadcount", 3); body.put("requiredPositionId", POSITION_ID);
        var created = json(mvc.perform(post("/api/v1/shift-templates")
            .header("Authorization", "Bearer " + managerToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(body))).andExpect(status().isCreated())
            .andExpect(jsonPath("$.requiredHeadcount").value(3))
            .andExpect(jsonPath("$.requiredPosition").value("Thu ngân"))
            .andExpect(jsonPath("$.overnight").value(false)));
        mvc.perform(get("/api/v1/shift-templates").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(created.get("id").asText()))
            .andExpect(jsonPath("$.items[0].requiredHeadcount").value(3))
            .andExpect(jsonPath("$.items[0].requiredPosition").value("Thu ngân"));
        assertThat(jdbc.queryForObject("select required_headcount from work_shift_templates where id = ?",
            Integer.class, UUID.fromString(created.get("id").asText()))).isEqualTo(3);
    }
    @ParameterizedTest
    @CsvSource({"22:00,06:00,false", "08:00,17:00,true"})
    void createShift_overnightFlagDoesNotMatchTimes_returns400(String start, String end, boolean overnight) throws Exception {
        var body = new HashMap<>(shiftBody(start, end, 0)); body.put("overnight", overnight);
        mvc.perform(post("/api/v1/shift-templates").header("Authorization", "Bearer " + managerToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_OVERNIGHT"));
        assertThat(jdbc.queryForObject("select count(*) from work_shift_templates", Integer.class)).isZero();
    }
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void createShift_headcountBelowOne_returns400(int count) throws Exception {
        var body = new HashMap<>(shiftBody("08:00", "17:00", 0)); body.put("requiredHeadcount", count);
        mvc.perform(post("/api/v1/shift-templates").header("Authorization", "Bearer " + managerToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"requiredHeadcount", "requiredPositionId", "overnight"})
    void createShift_missingRequiredField_returns400(String field) throws Exception {
        var body = new HashMap<>(shiftBody("08:00", "17:00", 0)); body.remove(field);
        mvc.perform(post("/api/v1/shift-templates").header("Authorization", "Bearer " + managerToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void createShift_invalidPositionId_returns400(String position) throws Exception {
        var body = new HashMap<>(shiftBody("08:00", "17:00", 0)); body.put("requiredPositionId", position);
        mvc.perform(post("/api/v1/shift-templates").header("Authorization", "Bearer " + managerToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }
    @Test void createShift_fractionalHeadcount_returns400InsteadOfTruncating() throws Exception {
        var body = new HashMap<>(shiftBody("08:00", "17:00", 0)); body.put("requiredHeadcount", 1.5);
        mvc.perform(post("/api/v1/shift-templates").header("Authorization", "Bearer " + managerToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from work_shift_templates", Integer.class)).isZero();
    }
    @Test void migration_v4Templates_preservesRowsAndBackfillsRequirements() {
        // An isolated schema in the Testcontainers DB simulates an already-deployed V4 database.
        var schema = "ss16_upgrade";
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("4").load().migrate();
            insertLegacyShift("Legacy day", "08:00", "17:00", 60);
            insertLegacyShift("Legacy night", "22:00", "06:00", 30);
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
            var rows = jdbc.queryForList("select name,required_headcount,required_position,overnight,break_minutes from ss16_upgrade.work_shift_templates order by name");
            assertThat(jdbc.queryForObject("select start_time from ss16_upgrade.work_shift_templates where name = 'Legacy day'", java.time.LocalTime.class)).isEqualTo(java.time.LocalTime.of(8, 0));
            assertThat(jdbc.queryForObject("select end_time from ss16_upgrade.work_shift_templates where name = 'Legacy night'", java.time.LocalTime.class)).isEqualTo(java.time.LocalTime.of(6, 0));
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0)).containsEntry("name", "Legacy day").containsEntry("required_headcount", 1)
                .containsEntry("required_position", null).containsEntry("overnight", false).containsEntry("break_minutes", 60);
            assertThat(rows.get(1)).containsEntry("name", "Legacy night").containsEntry("overnight", true).containsEntry("break_minutes", 30);
            assertThatThrownBy(() -> jdbc.update("update ss16_upgrade.work_shift_templates set required_headcount = 0"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("update ss16_upgrade.work_shift_templates set overnight = true where name = 'Legacy day'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            jdbc.execute("drop schema if exists ss16_upgrade cascade");
        }
    }
    private void insertLegacyShift(String name, String start, String end, int rest) {
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("insert into ss16_upgrade.work_shift_templates (id,name,start_time,end_time,break_minutes,created_by) values (?,?,?,?,?,?)");
            statement.setObject(1, UUID.randomUUID()); statement.setString(2, name);
            statement.setTime(3, java.sql.Time.valueOf(java.time.LocalTime.parse(start)), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            statement.setTime(4, java.sql.Time.valueOf(java.time.LocalTime.parse(end)), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            statement.setInt(5, rest); statement.setObject(6, admin.getId());
            return statement;
        });
    }
    @Test void createShift_overnight_returnsWorkingMinutes() throws Exception {
        var result = shift("22:00", "06:00", 30);
        assertThat(result.get("overnight").asBoolean()).isTrue();
        assertThat(result.get("workingMinutes").asInt()).isEqualTo(450);
        var id = UUID.fromString(result.get("id").asText());
        assertThat(jdbc.queryForObject("select start_time from work_shift_templates where id = ?", java.time.LocalTime.class, id)).isEqualTo(java.time.LocalTime.of(22, 0));
        assertThat(jdbc.queryForObject("select end_time from work_shift_templates where id = ?", java.time.LocalTime.class, id)).isEqualTo(java.time.LocalTime.of(6, 0));
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
