package com.company.hr.attendance.registration;

import com.company.hr.attendance.identity.PostgresTestConfig;
import com.company.hr.attendance.identity.domain.*;
import com.company.hr.attendance.identity.infrastructure.*;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Import(PostgresTestConfig.class)
class RegistrationIntegrationTests {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts; @Autowired LoginSessionRepository sessions;
    @Autowired ShiftTemplateRepository templates; @Autowired PasswordEncoder passwords;
    @MockitoBean(name="registrationClock") Clock clock;
    final AtomicReference<Instant> now = new AtomicReference<>();
    String employee, other, manager, admin; UUID employeeId, otherId, morning, night;
    static final String WEEK = "2026-11-16";
    @BeforeEach void setup() throws Exception {
        clean(); now.set(Instant.parse("2026-11-15T03:00:00Z")); when(clock.instant()).thenAnswer(ignored -> now.get());
        employee = token("registration-a", Role.EMPLOYEE); other = token("registration-b", Role.EMPLOYEE);
        manager = token("registration-manager", Role.MANAGER); admin = token("registration-admin", Role.ADMIN);
        UUID creator = UUID.randomUUID();
        morning = templates.saveAndFlush(new ShiftTemplate("Ca sáng", LocalTime.of(8,0), LocalTime.of(12,0),30,creator,1,null,null,false)).getId();
        night = templates.saveAndFlush(new ShiftTemplate("Ca đêm", LocalTime.of(22,0), LocalTime.of(6,0),30,creator,1,null,null,true)).getId();
    }
    @AfterEach void teardown() { clean(); }
    void clean() {
        jdbc.update("DELETE FROM shift_registrations"); jdbc.update("DELETE FROM registration_reopens");
        jdbc.update("DELETE FROM attendance_events"); jdbc.update("DELETE FROM attendance_records"); jdbc.update("DELETE FROM work_schedules");
        templates.deleteAll(); sessions.deleteAll(); jdbc.update("DELETE FROM identity_account_skills"); accounts.deleteAll();
    }
    String token(String name, Role role) throws Exception {
        var account = accounts.saveAndFlush(new Account(name+"@example.com",passwords.encode("Registration-test-password!"),name,role));
        if(name.equals("registration-a"))employeeId=account.getId(); if(name.equals("registration-b"))otherId=account.getId();
        return mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email",account.getEmail(),"password","Registration-test-password!"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
    String revision(String token) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/v1/registrations/me").param("weekStart",WEEK).header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("revision").asText();
    }
    String body(String revision, List<UUID> ids) throws Exception {
        return mapper.writeValueAsString(Map.of("weekStart",WEEK,"revision",revision,"shiftIds",ids));
    }
    org.springframework.test.web.servlet.ResultActions save(String revision, List<UUID> ids) throws Exception {
        return mvc.perform(put("/api/v1/registrations/me").header("Authorization","Bearer "+employee)
            .contentType(MediaType.APPLICATION_JSON).content(body(revision,ids)));
    }
    @Test void employeeCanSaveEditClearAndReloadOnSundayWithoutBeingAssignedSchedules() throws Exception {
        String original=revision(employee);
        save(original,List.of(morning)).andExpect(status().isOk()).andExpect(jsonPath("$.shifts[0].id").value(morning.toString()));
        String updated=revision(employee); assertThat(updated).isNotEqualTo(original);
        save(updated,List.of(morning,night)).andExpect(status().isOk()).andExpect(jsonPath("$.shifts.length()").value(2));
        save(revision(employee),List.of()).andExpect(status().isOk()).andExpect(jsonPath("$.shifts").isEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM work_schedules",Integer.class)).isZero();
    }
    @Test void outsideSundayAndOtherWeeksAreReadOnlyAndSavedSelectionsRemain() throws Exception {
        save(revision(employee),List.of(morning)).andExpect(status().isOk());
        now.set(Instant.parse("2026-11-15T17:00:00Z"));
        mvc.perform(get("/api/v1/registrations/me").param("weekStart",WEEK).header("Authorization","Bearer "+employee))
            .andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(false)).andExpect(jsonPath("$.shifts.length()").value(1));
        save(revision(employee),List.of()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REGISTRATION_CLOSED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shift_registrations",Integer.class)).isEqualTo(1);
        now.set(Instant.parse("2026-11-14T16:59:59Z")); save(revision(employee),List.of()).andExpect(status().isConflict());
    }
    @Test void lastMinuteIsStillOpenButTheNextVietnameseMondayIsClosed() throws Exception {
        now.set(Instant.parse("2026-11-15T16:59:59.999Z")); save(revision(employee),List.of(night)).andExpect(status().isOk());
        now.set(Instant.parse("2026-11-15T17:00:00Z")); save(revision(employee),List.of()).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/registrations/me").header("Authorization","Bearer "+employee))
            .andExpect(status().isOk()).andExpect(jsonPath("$.weekStart").value("2026-11-23"));
    }
    @Test void authorizationUsesTheAuthenticatedEmployeeAndRestrictsManagementAndLockedAccounts() throws Exception {
        mvc.perform(get("/api/v1/registrations/me")).andExpect(status().isUnauthorized());
        for(String token:List.of(manager,admin))mvc.perform(get("/api/v1/registrations/shifts").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        save(revision(employee),List.of(morning)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/registrations/me").param("employeeId",employeeId.toString()).header("Authorization","Bearer "+other))
            .andExpect(status().isOk()).andExpect(jsonPath("$.shifts").isEmpty());
        var locked=accounts.findById(employeeId).orElseThrow();locked.setLocked(true);accounts.saveAndFlush(locked);
        mvc.perform(get("/api/v1/registrations/me").header("Authorization","Bearer "+employee)).andExpect(status().isUnauthorized());
    }
    @Test void invalidOrDuplicateShiftsDoNotRemovePreviouslySavedSelections() throws Exception {
        save(revision(employee),List.of(morning)).andExpect(status().isOk()); String rev=revision(employee);
        save(rev,List.of(UUID.randomUUID())).andExpect(status().isBadRequest());
        save(rev,List.of(night,night)).andExpect(status().isBadRequest());
        assertThat(revision(employee)).isEqualTo(rev);
        mvc.perform(get("/api/v1/registrations/me").param("weekStart","2026-11-17").header("Authorization","Bearer "+employee)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/registrations/shifts").param("size","101").header("Authorization","Bearer "+employee)).andExpect(status().isBadRequest());
    }
    @Test void catalogIsPagedAndReadOnlyOnWeekdays() throws Exception {
        now.set(Instant.parse("2026-11-10T10:00:00Z"));
        mvc.perform(get("/api/v1/registrations/me").header("Authorization","Bearer "+employee))
            .andExpect(status().isOk()).andExpect(jsonPath("$.weekStart").value(WEEK)).andExpect(jsonPath("$.editable").value(false));
        mvc.perform(get("/api/v1/registrations/shifts").param("size","1").header("Authorization","Bearer "+employee))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.totalPages").value(2));
    }
    @Test void staleAndConcurrentTabsCannotOverwriteAnotherSelection() throws Exception {
        String rev=revision(employee); var pool=Executors.newFixedThreadPool(2); var barrier=new CyclicBarrier(2);
        try {
            var futures=List.of(morning,night).stream().map(id->pool.submit(()->{
                barrier.await(10,TimeUnit.SECONDS);
                return mvc.perform(put("/api/v1/registrations/me").header("Authorization","Bearer "+employee)
                    .contentType(MediaType.APPLICATION_JSON).content(body(rev,List.of(id)))).andReturn().getResponse().getStatus();
            })).toList();
            assertThat(List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM shift_registrations WHERE employee_id=?",Integer.class,employeeId)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
}
