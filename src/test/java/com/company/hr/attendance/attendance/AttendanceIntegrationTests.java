package com.company.hr.attendance.attendance;

import com.company.hr.attendance.attendance.domain.*;
import com.company.hr.attendance.attendance.infrastructure.AttendanceRecordRepository;
import com.company.hr.attendance.identity.domain.*;
import com.company.hr.attendance.identity.infrastructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired AttendanceRecordRepository attendanceRecords;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;

    private static final String PASSWORD = "Test-password-123!";
    private Account admin;
    private Account manager;
    private Account otherManager;
    private Account employee;
    private Account outsider;

    @BeforeEach
    void setup() {
        cleanup();
        admin = saveAccount("admin@example.com", "Quản trị", Role.ADMIN, null);
        manager = saveAccount("manager@example.com", "Quản lý A", Role.MANAGER, null);
        otherManager = saveAccount("other-manager@example.com", "Quản lý B", Role.MANAGER, null);
        employee = saveAccount("employee@example.com", "Nguyễn An", Role.EMPLOYEE, manager.getId());
        outsider = saveAccount("outsider@example.com", "Trần Bình", Role.EMPLOYEE, otherManager.getId());
    }

    @AfterEach
    void cleanup() {
        attendanceRecords.deleteAll();
        sessions.deleteAll();
        jdbc.update("UPDATE identity_accounts SET manager_id = NULL");
        jdbc.update("DELETE FROM identity_account_skills");
        jdbc.update("DELETE FROM identity_accounts");
    }

    private Account saveAccount(String email, String name, Role role, UUID managerId) {
        var account = new Account(email, passwords.encode(PASSWORD), name, role);
        account.updateDetails(email, name, role, null, List.of(), managerId);
        return accounts.saveAndFlush(account);
    }

    private String login(Account account) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", account.getEmail(), "password", PASSWORD))))
            .andExpect(status().isOk()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    void list_managerViewsManagedEmployeeAttendance_returnsPhotoPhotoLocationDeviceLocationAndResult() throws Exception {
        var record1 = attendanceRecords.save(new AttendanceRecord(
            employee.getId(),
            null,
            LocalDate.of(2026, 11, 7),
            "Ca sáng",
            AttendanceType.CHECK_IN,
            Instant.parse("2026-11-07T00:58:00Z"),
            "https://example.com/timemark/checkin-an.jpg",
            Instant.parse("2026-11-07T00:57:50Z"),
            "268 Lý Thường Kiệt, Quận 10, TP.HCM",
            "10.7726, 106.6579 (268 Lý Thường Kiệt, Q.10)",
            10.7726,
            106.6579,
            AttendanceStatus.ON_TIME,
            "Khớp vị trí và thời gian"
        ));
        attendanceRecords.save(new AttendanceRecord(
            outsider.getId(),
            null,
            LocalDate.of(2026, 11, 7),
            "Ca sáng",
            AttendanceType.CHECK_IN,
            Instant.parse("2026-11-07T01:15:00Z"),
            "https://example.com/timemark/checkin-binh.jpg",
            Instant.parse("2026-11-07T01:14:30Z"),
            "Quận 1, TP.HCM",
            "10.7756, 106.7004 (Quận 1, TP.HCM)",
            10.7756,
            106.7004,
            AttendanceStatus.LATE,
            "Trễ 15 phút"
        ));

        var managerToken = login(manager);
        mvc.perform(get("/api/v1/attendance-records")
                .header("Authorization", "Bearer " + managerToken)
                .param("from", "2026-11-01")
                .param("to", "2026-11-10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("totalElements").value(1))
            .andExpect(jsonPath("items[0].id").value(record1.getId().toString()))
            .andExpect(jsonPath("items[0].employeeName").value("Nguyễn An"))
            .andExpect(jsonPath("items[0].photoUrl").value("https://example.com/timemark/checkin-an.jpg"))
            .andExpect(jsonPath("items[0].photoLocation").value("268 Lý Thường Kiệt, Quận 10, TP.HCM"))
            .andExpect(jsonPath("items[0].deviceLocation").value("10.7726, 106.6579 (268 Lý Thường Kiệt, Q.10)"))
            .andExpect(jsonPath("items[0].deviceLatitude").value(10.7726))
            .andExpect(jsonPath("items[0].deviceLongitude").value(106.6579))
            .andExpect(jsonPath("items[0].status").value("ON_TIME"))
            .andExpect(jsonPath("items[0].checkType").value("CHECK_IN"))
            .andExpect(jsonPath("items[0].verificationNote").value("Khớp vị trí và thời gian"));
    }

    @Test
    void list_adminSeesAllRecordsAndCanFilterByStatus() throws Exception {
        attendanceRecords.save(new AttendanceRecord(
            employee.getId(), null, LocalDate.of(2026, 11, 7), "Ca sáng", AttendanceType.CHECK_IN,
            Instant.parse("2026-11-07T00:58:00Z"), "https://example.com/timemark/1.jpg",
            Instant.parse("2026-11-07T00:58:00Z"), "Cơ sở 1", "GPS Cơ sở 1", 10.77, 106.65,
            AttendanceStatus.ON_TIME, null
        ));
        attendanceRecords.save(new AttendanceRecord(
            outsider.getId(), null, LocalDate.of(2026, 11, 7), "Ca chiều", AttendanceType.CHECK_IN,
            Instant.parse("2026-11-07T06:20:00Z"), "https://example.com/timemark/2.jpg",
            Instant.parse("2026-11-07T06:20:00Z"), "Cơ sở 2", "GPS Cơ sở 2", 10.78, 106.68,
            AttendanceStatus.LATE, "Vào trễ 20 phút"
        ));

        var adminToken = login(admin);
        mvc.perform(get("/api/v1/attendance-records").header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("totalElements").value(2));

        mvc.perform(get("/api/v1/attendance-records")
                .header("Authorization", "Bearer " + adminToken)
                .param("status", "LATE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("totalElements").value(1))
            .andExpect(jsonPath("items[0].employeeName").value("Trần Bình"))
            .andExpect(jsonPath("items[0].status").value("LATE"));
    }

    @Test
    void createAndGet_enforcesScopeBetweenManagers() throws Exception {
        var managerToken = login(manager);
        var created = mvc.perform(post("/api/v1/attendance-records")
                .header("Authorization", "Bearer " + managerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.ofEntries(
                    Map.entry("employeeId", employee.getId()),
                    Map.entry("workDate", "2026-11-07"),
                    Map.entry("shiftName", "Ca sáng"),
                    Map.entry("checkType", "CHECK_OUT"),
                    Map.entry("photoUrl", "https://example.com/timemark/checkout.jpg"),
                    Map.entry("photoLocation", "268 Lý Thường Kiệt, Quận 10"),
                    Map.entry("deviceLocation", "10.7726, 106.6579"),
                    Map.entry("deviceLatitude", 10.7726),
                    Map.entry("deviceLongitude", 106.6579),
                    Map.entry("status", "EARLY_LEAVE"),
                    Map.entry("verificationNote", "Về sớm 10 phút")
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("status").value("EARLY_LEAVE"))
            .andReturn();

        var recordId = mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        var otherManagerToken = login(otherManager);
        mvc.perform(get("/api/v1/attendance-records/" + recordId)
                .header("Authorization", "Bearer " + otherManagerToken))
            .andExpect(status().isNotFound());
    }
}
