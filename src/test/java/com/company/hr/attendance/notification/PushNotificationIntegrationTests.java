package com.company.hr.attendance.notification;

import com.company.hr.attendance.identity.PostgresTestConfig;
import com.company.hr.attendance.identity.domain.*;
import com.company.hr.attendance.identity.infrastructure.*;
import com.company.hr.attendance.notification.application.*;
import com.company.hr.attendance.notification.infrastructure.PushRepository;
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
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="app.notifications.cron-secret=test-only-cron-secret-more-than-32-characters") @AutoConfigureMockMvc @ActiveProfiles("test") @Import(PostgresTestConfig.class)
class PushNotificationIntegrationTests {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;@Autowired LoginSessionRepository sessions;@Autowired PasswordEncoder passwords;
    @Autowired ShiftTemplateRepository templates;@Autowired RegistrationReminderService reminders;@Autowired PushRepository repository;
    @MockitoBean com.company.hr.attendance.notification.infrastructure.ReminderTrigger trigger;
    @MockitoBean PushGateway gateway;@MockitoBean(name="notificationClock") Clock clock;
    final AtomicReference<Instant> now=new AtomicReference<>();
    String a,b,manager;UUID aId,bId;static final LocalDate WEEK=LocalDate.of(2026,11,16);
    @BeforeEach void setup() throws Exception {
        clean();now.set(Instant.parse("2026-11-14T17:00:00Z"));when(clock.instant()).thenAnswer(ignored->now.get());
        when(gateway.available()).thenReturn(true);when(gateway.send(any(),anyString(),any(),anyString(),any(),any())).thenReturn(PushGateway.Result.SENT);
        a=login("push-a",Role.EMPLOYEE);b=login("push-b",Role.EMPLOYEE);manager=login("push-manager",Role.MANAGER);
        aId=accounts.findByEmail("push-a@example.com").orElseThrow().getId();bId=accounts.findByEmail("push-b@example.com").orElseThrow().getId();
    }
    @AfterEach void teardown(){clean();}
    void clean(){
        jdbc.update("DELETE FROM notification_deliveries");jdbc.update("DELETE FROM notification_devices");
        jdbc.update("DELETE FROM shift_registrations");jdbc.update("DELETE FROM registration_reopens");
        jdbc.update("DELETE FROM attendance_events");jdbc.update("DELETE FROM attendance_records");jdbc.update("DELETE FROM work_schedules");
        templates.deleteAll();sessions.deleteAll();jdbc.update("DELETE FROM identity_account_skills");jdbc.update("UPDATE identity_accounts SET manager_id=NULL");accounts.deleteAll();
    }
    String login(String name,Role role)throws Exception{
        var account=accounts.saveAndFlush(new Account(name+"@example.com",passwords.encode("Push-test-password!"),name,role));
        return mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email",account.getEmail(),"password","Push-test-password!"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
    org.springframework.test.web.servlet.ResultActions subscribe(String auth,String token)throws Exception{
        return mvc.perform(put("/api/v1/notifications/devices").header("Authorization","Bearer "+auth).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("token",token))));
    }
    int count(String table){return jdbc.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    void twoDevices()throws Exception{subscribe(a,"fake-token-a").andExpect(status().isNoContent());subscribe(b,"fake-token-b").andExpect(status().isNoContent());}
    @Test void sundayOpeningIsSentOncePerDeviceAndWeekEvenWhenPollingRepeats()throws Exception{
        twoDevices();now.set(Instant.parse("2026-11-14T16:59:59Z"));reminders.run();verify(gateway,never()).send(any(),anyString(),any(),anyString(),any(),any());
        now.set(Instant.parse("2026-11-14T17:00:00Z"));reminders.run();reminders.run();
        verify(gateway,times(2)).send(any(),anyString(),eq(WEEK),eq("OPEN"),eq(Instant.parse("2026-11-15T17:00:00Z")),any());
        assertThat(count("notification_deliveries")).isEqualTo(2);
        now.set(Instant.parse("2026-11-21T17:00:00Z"));reminders.run();assertThat(count("notification_deliveries")).isEqualTo(4);
    }
    @Test void nearClosureOnlyEmployeesWithoutASelectedShiftReceiveTheAdditionalReminder()throws Exception{
        twoDevices();var shift=templates.saveAndFlush(new ShiftTemplate("Ca sáng",LocalTime.of(8,0),LocalTime.of(12,0),0,UUID.randomUUID(),1,null,null,false));
        jdbc.update("INSERT INTO shift_registrations(id,employee_id,week_start,shift_template_id) VALUES (?,?,?,?)",UUID.randomUUID(),aId,WEEK,shift.getId());
        now.set(Instant.parse("2026-11-15T14:59:59Z"));reminders.run();clearInvocations(gateway);
        now.set(Instant.parse("2026-11-15T15:00:00Z"));reminders.run();reminders.run();
        verify(gateway,times(1)).send(eq(bId),eq("fake-token-b"),eq(WEEK),eq("REMINDER"),any(),any());
        verify(gateway,never()).send(eq(aId),anyString(),any(),eq("REMINDER"),any(),any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_deliveries WHERE status='SKIPPED'",Integer.class)).isEqualTo(1);
    }
    @Test void failedDeliveryRetriesWithBackoffAndCannotBeSentAfterMonday()throws Exception{
        subscribe(a,"fake-token-a").andExpect(status().isNoContent());when(gateway.send(any(),anyString(),any(),anyString(),any(),any())).thenReturn(PushGateway.Result.RETRY,PushGateway.Result.SENT);
        reminders.run();now.set(now.get().plusSeconds(59));reminders.run();verify(gateway,times(1)).send(any(),anyString(),any(),anyString(),any(),any());
        now.set(now.get().plusSeconds(1));reminders.run();verify(gateway,times(2)).send(any(),anyString(),any(),anyString(),any(),any());
        when(gateway.send(any(),anyString(),any(),anyString(),any(),any())).thenReturn(PushGateway.Result.RETRY);
        now.set(Instant.parse("2026-11-15T16:59:30Z"));reminders.run();clearInvocations(gateway);
        now.set(Instant.parse("2026-11-15T17:00:00Z"));reminders.run();verify(gateway,never()).send(any(),anyString(),any(),anyString(),any(),any());
    }
    @Test void unregisteredTokensAreRemovedAndRevokedOrLockedAccountsAreSkipped()throws Exception{
        twoDevices();when(gateway.send(eq(aId),anyString(),any(),anyString(),any(),any())).thenReturn(PushGateway.Result.INVALID_TOKEN);
        var locked=accounts.findById(bId).orElseThrow();locked.setLocked(true);accounts.saveAndFlush(locked);
        reminders.run();assertThat(count("notification_devices")).isEqualTo(1);
        verify(gateway,never()).send(eq(bId),anyString(),any(),anyString(),any(),any());
    }
    @Test void explicitLogoutStopsPushButJwtExpiryAloneDoesNotCancelTheConsent()throws Exception{
        twoDevices();mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+a)).andExpect(status().isNoContent());
        jdbc.update("UPDATE identity_sessions SET expires_at=? WHERE account_id=?",OffsetDateTime.now().minusDays(1),bId);
        reminders.run();verify(gateway,never()).send(eq(aId),anyString(),any(),anyString(),any(),any());
        verify(gateway,times(1)).send(eq(bId),anyString(),any(),eq("OPEN"),any(),any());
    }
    @Test void deviceOwnershipUsesJwtAndSharedBrowserRebindingClearsOldQueuedWork()throws Exception{
        subscribe(a,"shared-token").andExpect(status().isNoContent());repository.enqueue(WEEK,"OPEN",now.get());
        subscribe(b,"shared-token").andExpect(status().isNoContent());
        assertThat(count("notification_devices")).isEqualTo(1);assertThat(count("notification_deliveries")).isZero();
        assertThat(jdbc.queryForObject("SELECT employee_id FROM notification_devices",UUID.class)).isEqualTo(bId);
        mvc.perform(delete("/api/v1/notifications/devices").header("Authorization","Bearer "+a).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"shared-token\"}")).andExpect(status().isNoContent());
        assertThat(count("notification_devices")).isEqualTo(1);
        mvc.perform(delete("/api/v1/notifications/devices").header("Authorization","Bearer "+b).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"shared-token\"}")).andExpect(status().isNoContent());
        assertThat(count("notification_devices")).isZero();
    }
    @Test void roleValidationMissingConfigAndTestPushAreScopedToOwnDevice()throws Exception{
        mvc.perform(get("/api/v1/notifications/config")).andExpect(status().isUnauthorized());
        subscribe(manager,"fake-manager-token").andExpect(status().isForbidden());subscribe(a,"").andExpect(status().isBadRequest());
        when(gateway.available()).thenReturn(false);subscribe(a,"fake-token-a").andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("PUSH_NOT_CONFIGURED"));
        reminders.run();assertThat(count("notification_deliveries")).isZero();when(gateway.available()).thenReturn(true);
        subscribe(a,"fake-token-a").andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/notifications/test").header("Authorization","Bearer "+b).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fake-token-a\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/notifications/test").header("Authorization","Bearer "+a).contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"fake-token-a\"}")).andExpect(status().isNoContent());
        verify(gateway).send(eq(aId),eq("fake-token-a"),any(),eq("TEST"),any(),any());
    }
    @Test void twoWorkersClaimEachDeliveryOnlyOnce()throws Exception{
        twoDevices();var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try{
            var one=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);reminders.run();return true;});
            var two=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);reminders.run();return true;});
            one.get(20,TimeUnit.SECONDS);two.get(20,TimeUnit.SECONDS);
            verify(gateway,times(1)).send(eq(aId),anyString(),any(),eq("OPEN"),any(),any());
            verify(gateway,times(1)).send(eq(bId),anyString(),any(),eq("OPEN"),any(),any());
        }finally{pool.shutdownNow();}
    }
    @Test void staleLeaseCannotAcknowledgeAClaimTakenOverByANewerWorker()throws Exception{
        subscribe(a,"fake-token-a").andExpect(status().isNoContent());repository.enqueue(WEEK,"OPEN",now.get());
        var old=repository.claim(now.get()).orElseThrow();var current=repository.claim(now.get().plusSeconds(301)).orElseThrow();
        assertThat(old.id()).isEqualTo(current.id());assertThat(old.leaseToken()).isNotEqualTo(current.leaseToken());
        repository.finish(old,"SENT",now.get(),null);
        assertThat(jdbc.queryForObject("SELECT status FROM notification_deliveries",String.class)).isEqualTo("PENDING");
        repository.finish(current,"SENT",now.get(),null);
        assertThat(jdbc.queryForObject("SELECT status FROM notification_deliveries",String.class)).isEqualTo("SENT");
    }

    @Test void cronRequiresItsOwnSecretEvenForAuthenticatedEmployeesAndQueuesWithoutWaitingForFcm()throws Exception{
        var path="/api/v1/notification-jobs/registration-reminders";
        mvc.perform(post(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization","Bearer "+a)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("X-Reminder-Cron-Key","wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("X-Reminder-Cron-Key","test-only-cron-secret-more-than-32-characters")).andExpect(status().isAccepted());
        verify(trigger,times(1)).trigger();
        when(gateway.available()).thenReturn(false);
        mvc.perform(post(path).header("X-Reminder-Cron-Key","test-only-cron-secret-more-than-32-characters")).andExpect(status().isServiceUnavailable());
        verify(trigger,times(1)).trigger();
    }
    @Test void subscriptionLimitPreventsAnEmployeeFromCreatingUnboundedDevices()throws Exception{
        for(int i=0;i<10;i++)subscribe(a,"device-token-"+i).andExpect(status().isNoContent());
        subscribe(a,"device-token-10").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PUSH_DEVICE_LIMIT"));
        subscribe(a,"device-token-0").andExpect(status().isNoContent());
        assertThat(count("notification_devices")).isEqualTo(10);
    }
}
