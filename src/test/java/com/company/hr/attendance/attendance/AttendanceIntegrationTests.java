package com.company.hr.attendance.attendance;

import com.company.hr.attendance.identity.PostgresTestConfig;
import com.company.hr.attendance.identity.domain.Account;
import com.company.hr.attendance.identity.domain.Role;
import com.company.hr.attendance.identity.infrastructure.AccountRepository;
import com.company.hr.attendance.identity.infrastructure.LoginSessionRepository;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class AttendanceIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired ShiftTemplateRepository templates;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    private static final String PASSWORD = "Test-password-123!";
    private UUID shiftId;
    private UUID absentId;
    private String employeeToken, managerToken, outsiderToken;

    @BeforeEach void seed() throws Exception {
        clear();
        var manager = account("manager", Role.MANAGER, null);
        var employee = account("employee", Role.EMPLOYEE, manager.getId());
        var outsider = account("outsider", Role.EMPLOYEE, null);
        employeeToken = token(employee); managerToken = token(manager); outsiderToken = token(outsider);
        var template = templates.saveAndFlush(new ShiftTemplate("Ca sáng", LocalTime.of(8, 0), LocalTime.of(17, 0),
            60, manager.getId(), 1, null, null, false));
        var now = Instant.now();
        shiftId = UUID.randomUUID(); absentId = UUID.randomUUID();
        schedule(shiftId, employee.getId(), template.getId(), now.minusSeconds(60), now.plusSeconds(3600));
        schedule(absentId, employee.getId(), template.getId(), now.minusSeconds(7200), now.minusSeconds(3600));
    }
    @AfterEach void cleanup() { clear(); }
    private void clear() {
        jdbc.update("DELETE FROM attendance_records");
        jdbc.update("DELETE FROM attendance_events");
        jdbc.update("DELETE FROM work_schedules");
        jdbc.update("DELETE FROM work_shift_templates");
        sessions.deleteAll();
        jdbc.update("DELETE FROM identity_account_skills");
        jdbc.update("DELETE FROM identity_accounts");
    }
    private Account account(String name, Role role, UUID managerId) {
        var account = new Account(name + "@example.com", encoder.encode(PASSWORD), name, role);
        account.updateDetails(account.getEmail(), name, role, null, List.of(), managerId);
        return accounts.saveAndFlush(account);
    }
    private String token(Account account) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", account.getEmail(), "password", PASSWORD))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("accessToken").asText();
    }
    private void schedule(UUID id, UUID employee, UUID template, Instant start, Instant end) {
        var date = LocalDate.ofInstant(start, ZoneId.of("Asia/Ho_Chi_Minh"));
        jdbc.update("""
            INSERT INTO work_schedules(id, employee_id, shift_template_id, work_date, shift_name,
                starts_at, ends_at, break_minutes, timezone, created_by)
            VALUES (?, ?, ?, ?, 'Ca sáng', ?, ?, 60, 'Asia/Ho_Chi_Minh', ?)
            """, id, employee, template, date, java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), employee);
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(UUID id, boolean location) {
        var request = multipart("/api/v1/attendance/events")
            .file(new MockMultipartFile("photo", "timemark.jpg", "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}))
            .param("scheduleId", id.toString()).param("type", "CHECK_IN");
        if (location) request.param("locatedAt", Instant.now().toString())
            .param("latitude", "10.7726").param("longitude", "106.6579").param("accuracyMeters", "5.0")
            .param("timemarkCapturedAt", Instant.now().toString())
            .param("timemarkLatitude", "10.7726").param("timemarkLongitude", "106.6579");
        return request;
    }
    @Test void uploadIsPrivateUniqueAndResultsIncludeAbsence() throws Exception {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '7' AND success", Integer.class)).isEqualTo(1);
        mvc.perform(upload(shiftId, false).header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_events", Integer.class)).isZero();
        mvc.perform(upload(shiftId, true).header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isForbidden());
        var created = mapper.readTree(mvc.perform(upload(shiftId, true).header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("CHECK_IN"))
            .andReturn().getResponse().getContentAsString());
        var photoId = created.get("id").asText();
        JsonNode recordPage = mapper.readTree(mvc.perform(get("/api/v1/attendance-records")
            .header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(recordPage.get("totalElements").asInt()).isEqualTo(1);
        assertThat(recordPage.at("/items/0/id").asText()).isEqualTo(photoId);
        assertThat(recordPage.at("/items/0/status").asText()).isEqualTo("ON_TIME");
        assertThat(recordPage.at("/items/0/photoUrl").asText())
            .isEqualTo("/api/v1/attendance/events/" + photoId + "/photo");
        mvc.perform(get("/api/v1/attendance-records/{id}", photoId)
            .header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isNotFound());
        mvc.perform(upload(shiftId, true).header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_RECORDED"));
        mvc.perform(delete("/api/v1/work-schedules/{id}", shiftId).header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCHEDULE_HAS_ATTENDANCE"));
        mvc.perform(get("/api/v1/attendance/events/{id}/photo", photoId).header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG));
        mvc.perform(get("/api/v1/attendance/events/{id}/photo", photoId).header("Authorization", "Bearer " + outsiderToken))
            .andExpect(status().isForbidden());
        var today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        JsonNode result = mapper.readTree(mvc.perform(get("/api/v1/attendance/results")
            .header("Authorization", "Bearer " + managerToken)
            .param("from", today.minusDays(1).toString()).param("to", today.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(result.get("items").size()).isEqualTo(2);
        assertThat(result.get("items").toString()).contains("ABSENT").contains("IN_PROGRESS");
    }

    @Test void checkInAndCheckOutAreClassifiedAndSavedAsTwoInspectionRecords() throws Exception {
        var now = Instant.now();
        jdbc.update("UPDATE work_schedules SET starts_at = ?, ends_at = ? WHERE id = ?",
            java.sql.Timestamp.from(now.minusSeconds(360)), java.sql.Timestamp.from(now.plusSeconds(3600)), shiftId);
        mvc.perform(upload(shiftId, true).header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated());
        mvc.perform(upload(shiftId, true).with(request -> { request.setParameter("type", "CHECK_OUT"); return request; }).header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("CHECK_OUT"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_events WHERE schedule_id = ?", Integer.class, shiftId)).isEqualTo(2);
        var today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var response = mapper.readTree(mvc.perform(get("/api/v1/attendance/results")
            .header("Authorization", "Bearer " + managerToken)
            .param("from", today.minusDays(1).toString()).param("to", today.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var items = response.get("items");
        JsonNode classified = null;
        for (JsonNode item : items) if (item.get("scheduleId").asText().equals(shiftId.toString())) classified = item;
        assertThat(classified).isNotNull();
        assertThat(classified.get("arrival").asText()).isEqualTo("LATE");
        assertThat(classified.get("departure").asText()).isEqualTo("EARLY_LEAVE");
        assertThat(classified.get("overall").asText()).isEqualTo("LATE_AND_EARLY_LEAVE");
        assertThat(classified.get("lateMinutes").asLong()).isGreaterThanOrEqualTo(6);
        assertThat(classified.get("earlyMinutes").asLong()).isGreaterThanOrEqualTo(59);
        mvc.perform(get("/api/v1/attendance/results").header("Authorization", "Bearer " + employeeToken)
            .param("from", today.toString()).param("to", today.toString())).andExpect(status().isForbidden());
    }
}
