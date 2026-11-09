package com.company.hr.attendance.organization;

import com.company.hr.attendance.identity.PostgresTestConfig;
import com.company.hr.attendance.identity.domain.*;
import com.company.hr.attendance.identity.infrastructure.*;
import com.company.hr.attendance.attendance.api.AttendanceModels.TimemarkEvidence;
import com.company.hr.attendance.attendance.application.AttendanceException;
import com.company.hr.attendance.attendance.application.AttendanceVerificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.attendance.verification.workplace-latitude=", "app.attendance.verification.workplace-longitude="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class WorkplaceIntegrationTests {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.company.hr.attendance.organization.infrastructure.GoongPlacesClient places;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkplaceApi workplaces;
    @Autowired AttendanceVerificationService verification;
    String managerToken, adminToken, employeeToken;
    UUID managerId, employeeId;
    @Autowired com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository templates;
    static final String PASSWORD = "Workplace-test-password!";
    @BeforeEach void seed() throws Exception {
        clear(); managerToken = token("workplace-manager", Role.MANAGER);
        adminToken = token("workplace-admin", Role.ADMIN); employeeToken = token("workplace-employee", Role.EMPLOYEE);
    }
    @AfterEach void cleanup() { clear(); }
    void clear() {
        jdbc.update("DELETE FROM organization_workplace_settings");
        jdbc.update("DELETE FROM attendance_events"); jdbc.update("DELETE FROM attendance_records");
        jdbc.update("DELETE FROM work_schedules"); jdbc.update("DELETE FROM work_shift_templates");
        jdbc.update("DELETE FROM attendance_registered_devices"); sessions.deleteAll();
        jdbc.update("DELETE FROM identity_account_skills"); jdbc.update("DELETE FROM identity_accounts");
    }
    String token(String name, Role role) throws Exception {
        var account = accounts.saveAndFlush(new Account(name + "@example.com", passwords.encode(PASSWORD), name, role));
        if (role == Role.MANAGER) managerId = account.getId();
        if (role == Role.EMPLOYEE) employeeId = account.getId();
        return mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", account.getEmail(), "password", PASSWORD))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }
    Map<String, Object> body(long version, double latitude, int tolerance) {
        return Map.of("latitude", latitude, "longitude", 106.6579, "radiusMeters", 100,
            "gpsToleranceMeters", tolerance, "version", version);
    }
    org.springframework.test.web.servlet.ResultActions save(String token, Map<String, Object> body) throws Exception {
        return mvc.perform(put("/api/v1/workplace-settings").header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    @Test void missingConfigurationIsAnEmpty200AndManagerCanPersistIt() throws Exception {
        mvc.perform(get("/api/v1/workplace-settings").header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.configured").value(false))
            .andExpect(jsonPath("$.latitude").isEmpty()).andExpect(jsonPath("$.gpsToleranceMeters").value(30));
        save(managerToken, body(0, 10.7726, 30)).andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(true)).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/v1/workplace-settings").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.latitude").value(10.7726));
        assertThat(workplaces.current()).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_workplace_settings", Integer.class)).isEqualTo(1);
    }
    @Test void employeesAndAnonymousUsersCannotReadOrChangeConfiguration() throws Exception {
        mvc.perform(get("/api/v1/workplace-settings")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/workplace-settings").header("Authorization", "Bearer " + employeeToken)).andExpect(status().isForbidden());
        save(employeeToken, body(0, 10.7726, 30)).andExpect(status().isForbidden());
        assertThat(workplaces.current()).isEmpty();
    }
    @Test void validatesRangesAndDoesNotOverwriteConcurrentUpdates() throws Exception {
        var invalidRadius = new java.util.HashMap<String, Object>(body(0, 10.7726, 30));
        invalidRadius.put("radiusMeters", 9); save(adminToken, invalidRadius).andExpect(status().isBadRequest());
        invalidRadius.put("radiusMeters", 5001); save(adminToken, invalidRadius).andExpect(status().isBadRequest());
        save(adminToken, body(0, 91, 30)).andExpect(status().isBadRequest());
        save(adminToken, body(0, 10.7726, 19)).andExpect(status().isBadRequest());
        save(adminToken, body(0, 10.7726, 51)).andExpect(status().isBadRequest());
        save(adminToken, body(0, 10.7726, 20)).andExpect(status().isOk());
        save(managerToken, body(0, 10.8, 50)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_WORKPLACE"));
        save(managerToken, body(1, 10.7726, 50)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        save(adminToken, body(1, 10.9, 30)).andExpect(status().isConflict());
        assertThat(workplaces.current().orElseThrow().gpsToleranceMeters()).isEqualTo(50);
    }
    @Test void savingAndUpdatingConfigurationImmediatelyChangesAttendanceVerification() throws Exception {
        var now = Instant.now(); var evidence = new TimemarkEvidence(now, 10.7726, 106.6579);
        assertThatThrownBy(() -> verification.verify(10.7726, 106.6579, 5.0, evidence, now))
            .isInstanceOfSatisfying(AttendanceException.class, error -> assertThat(error.getCode()).isEqualTo("WORKPLACE_NOT_CONFIGURED"));
        save(managerToken, body(0, 10.7726, 30)).andExpect(status().isOk());
        assertThatCode(() -> verification.verify(10.7726, 106.6579, 5.0, evidence, now)).doesNotThrowAnyException();
        save(adminToken, body(1, 11.0, 50)).andExpect(status().isOk());
        assertThatThrownBy(() -> verification.verify(10.7726, 106.6579, 5.0, evidence, now))
            .isInstanceOfSatisfying(AttendanceException.class, error -> assertThat(error.getCode()).isEqualTo("ATTENDANCE_VERIFICATION_FAILED"));
    }

    @Test void configuredWorkplaceUnblocksTheActualEmployeeAttendanceEndpoint() throws Exception {
        var template = templates.saveAndFlush(new com.company.hr.attendance.workschedule.domain.ShiftTemplate(
            "Workplace shift", java.time.LocalTime.of(8, 0), java.time.LocalTime.of(17, 0), 60, managerId, 1, null, null, false));
        var now = Instant.now(); var shiftId = UUID.randomUUID();
        var date = java.time.LocalDate.ofInstant(now.minusSeconds(3600), java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        jdbc.update("""
            INSERT INTO work_schedules(id, employee_id, shift_template_id, work_date, shift_name, starts_at, ends_at, break_minutes, timezone, created_by)
            VALUES (?, ?, ?, ?, 'Workplace shift', ?, ?, 60, 'Asia/Ho_Chi_Minh', ?)
            """, shiftId, employeeId, template.getId(), date, java.sql.Timestamp.from(now.minusSeconds(3600)), java.sql.Timestamp.from(now.plusSeconds(3600)), managerId);
        mvc.perform(upload(shiftId).header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("WORKPLACE_NOT_CONFIGURED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_events", Integer.class)).isZero();
        save(managerToken, body(0, 10.7726, 30)).andExpect(status().isOk());
        mvc.perform(upload(shiftId).header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.type").value("CHECK_IN"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_events", Integer.class)).isEqualTo(1);
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(UUID shiftId) {
        return multipart("/api/v1/attendance/events").file(new org.springframework.mock.web.MockMultipartFile(
            "photo", "camera.jpg", "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1}))
            .param("scheduleId", shiftId.toString()).param("type", "CHECK_IN")
            .param("locatedAt", Instant.now().toString()).param("latitude", "10.7726").param("longitude", "106.6579")
            .param("accuracyMeters", "5").param("timemarkCapturedAt", Instant.now().toString())
            .param("timemarkLatitude", "10.7726").param("timemarkLongitude", "106.6579");
    }

    @Test void managersAndAdminsCanSearchAndResolveWithoutSavingSettings() throws Exception {
        org.mockito.Mockito.when(places.search("Bách Khoa")).thenReturn(java.util.List.of(
            new com.company.hr.attendance.organization.api.PlaceModels.Suggestion("id/+=", "Đại học Bách Khoa")));
        org.mockito.Mockito.when(places.detail("id/+=")).thenReturn(
            new com.company.hr.attendance.organization.api.PlaceModels.Place("id/+=", "268 Lý Thường Kiệt", 10.7726, 106.6579));
        mvc.perform(get("/api/v1/workplace-settings/places").param("q", "  Bách Khoa  ").header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$[0].placeId").value("id/+=")).andExpect(jsonPath("$[0].label").value("Đại học Bách Khoa"));
        mvc.perform(get("/api/v1/workplace-settings/places/detail").param("placeId", "id/+=").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.latitude").value(10.7726)).andExpect(jsonPath("$.longitude").value(106.6579));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_workplace_settings", Integer.class)).isZero();
    }
    @Test void addressSearchRejectsAnonymousEmployeesAndLockedManagersBeforeProviderCalls() throws Exception {
        mvc.perform(get("/api/v1/workplace-settings/places").param("q", "Bách Khoa")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/workplace-settings/places/detail").param("placeId", "id").header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isForbidden());
        var manager = accounts.findById(managerId).orElseThrow(); manager.setLocked(true); accounts.saveAndFlush(manager);
        mvc.perform(get("/api/v1/workplace-settings/places").param("q", "Bách Khoa").header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(places);
    }
    @Test void addressQueriesAreValidatedAndErrorsUseTheExistingApiContract() throws Exception {
        mvc.perform(get("/api/v1/workplace-settings/places").param("q", "ab").header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PLACE_QUERY"));
        mvc.perform(get("/api/v1/workplace-settings/places/detail").param("placeId", " ").header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(places);
        org.mockito.Mockito.when(places.search("Bách Khoa")).thenThrow(new com.company.hr.attendance.organization.application.WorkplaceException(
            org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "PLACE_SEARCH_NOT_CONFIGURED", "Tìm địa chỉ chưa được cấu hình."));
        mvc.perform(get("/api/v1/workplace-settings/places").param("q", "Bách Khoa").header("Authorization", "Bearer " + managerToken))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PLACE_SEARCH_NOT_CONFIGURED"));
    }
}
