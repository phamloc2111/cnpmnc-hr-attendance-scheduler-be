package com.company.hr.attendance.diligence;

import com.company.hr.attendance.identity.PostgresTestConfig;
import com.company.hr.attendance.identity.domain.*;
import com.company.hr.attendance.identity.infrastructure.*;
import com.company.hr.attendance.attendance.api.AttendanceModels.EventType;
import com.company.hr.attendance.attendance.infrastructure.AttendanceEventRepository;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Import(PostgresTestConfig.class)
class DiligenceIntegrationTests {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts;@Autowired LoginSessionRepository sessions;@Autowired PasswordEncoder encoder;
    @Autowired ShiftTemplateRepository templates;@Autowired AttendanceEventRepository events;
    @MockitoBean(name="diligenceClock") Clock clock;
    String managerToken,adminToken,employeeToken,otherToken;Account manager,employee,idle,outside;UUID template;
    static final Instant NOW=Instant.parse("2026-12-01T01:00:00Z");
    @BeforeEach void setup()throws Exception{
        clear();when(clock.instant()).thenReturn(NOW);
        manager=account("Manager",Role.MANAGER,null);var other=account("OtherManager",Role.MANAGER,null);var admin=account("Administrator",Role.ADMIN,null);
        employee=account("An",Role.EMPLOYEE,manager.getId());idle=account("Binh",Role.EMPLOYEE,manager.getId());outside=account("Outside",Role.EMPLOYEE,other.getId());
        managerToken=login(manager);adminToken=login(admin);employeeToken=login(employee);otherToken=login(other);
        template=templates.saveAndFlush(new ShiftTemplate("Morning",LocalTime.of(8,0),LocalTime.of(17,0),60,manager.getId(),1,null,null,false)).getId();
    }
    @AfterEach void cleanup(){clear();}
    void clear(){
        jdbc.update("DELETE FROM notification_deliveries");jdbc.update("DELETE FROM notification_devices");
        jdbc.update("DELETE FROM shift_registrations");jdbc.update("DELETE FROM registration_reopens");
        jdbc.update("DELETE FROM diligence_review_audit");jdbc.update("DELETE FROM diligence_day_reviews");
        jdbc.update("DELETE FROM attendance_records");jdbc.update("DELETE FROM attendance_events");jdbc.update("DELETE FROM work_schedules");
        templates.deleteAll();sessions.deleteAll();jdbc.update("DELETE FROM identity_account_skills");jdbc.update("UPDATE identity_accounts SET manager_id=NULL");accounts.deleteAll();
    }
    Account account(String name,Role role,UUID managerId){
        var account=new Account(name.toLowerCase(Locale.ROOT)+"@example.com",encoder.encode("Test-password-123!"),name,role);
        account.updateDetails(account.getEmail(),name,role,null,List.of(),managerId);return accounts.saveAndFlush(account);
    }
    String login(Account account)throws Exception{
        return mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email",account.getEmail(),"password","Test-password-123!"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();
    }
    UUID shift(UUID employeeId,LocalDate date,Instant start){
        var id=UUID.randomUUID();jdbc.update("INSERT INTO work_schedules(id,employee_id,shift_template_id,work_date,shift_name,starts_at,ends_at,break_minutes,timezone,created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
            id,employeeId,template,date,"Morning",java.sql.Timestamp.from(start),java.sql.Timestamp.from(start.plusSeconds(8*3600)),0,"Asia/Ho_Chi_Minh",manager.getId());return id;
    }
    void event(UUID schedule,UUID employeeId,Instant time,EventType type){
        events.insert(UUID.randomUUID(),schedule,employeeId,type,time,time,10.7726,106.6579,5,"image/jpeg",new byte[]{1});
    }
    UUID late(int day,int minutes){
        var start=LocalDate.of(2026,11,day).atTime(8,0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        var id=shift(employee.getId(),LocalDate.of(2026,11,day),start);event(id,employee.getId(),start.plusSeconds(minutes*60L),EventType.CHECK_IN);event(id,employee.getId(),start.plusSeconds(8*3600),EventType.CHECK_OUT);return id;
    }
    JsonNode month(String auth,String value)throws Exception{
        return mapper.readTree(mvc.perform(get("/api/v1/diligence").param("month",value).header("Authorization","Bearer "+auth)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString());
    }
    JsonNode detail(String auth,UUID id,String value)throws Exception{
        return mapper.readTree(mvc.perform(get("/api/v1/diligence/employees/"+id).param("month",value).header("Authorization","Bearer "+auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void sampleNinetyQualifiesThenSeventyFailsWithoutDoubleDeductions()throws Exception{
        late(1,10);late(2,14);var first=detail(managerToken,employee.getId(),"2026-11");
        assertThat(first.at("/employee/score").asInt()).isEqualTo(90);assertThat(first.at("/employee/rewardStatus").asText()).isEqualTo("ELIGIBLE");
        var id=late(3,35);var second=detail(managerToken,employee.getId(),"2026-11");
        assertThat(second.at("/employee/score").asInt()).isEqualTo(70);assertThat(second.at("/employee/rewardStatus").asText()).isEqualTo("INELIGIBLE");
        assertThat(second.path("finalized").asBoolean()).isTrue();assertThat(second.at("/policy/graceMinutes").asInt()).isEqualTo(5);

        assertThat(detail(managerToken,employee.getId(),"2026-11").at("/employee/score").asInt()).isEqualTo(70);
        assertThat(detail(managerToken,employee.getId(),"2026-11").at("/employee/checkInCount").asInt()).isEqualTo(3);
        assertThat(second.has("amount")).isFalse();
    }
    @Test void newMonthResetsAutomaticallyAndBoundaryIsVietnamTime()throws Exception{
        late(1,35);assertThat(detail(managerToken,employee.getId(),"2026-12").at("/employee/score").asInt()).isEqualTo(100);
        when(clock.instant()).thenReturn(Instant.parse("2026-11-30T16:59:59Z"));
        var before=mapper.readTree(mvc.perform(get("/api/v1/diligence").header("Authorization","Bearer "+managerToken)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(before.path("month").asText()).isEqualTo("2026-11");assertThat(before.path("finalized").asBoolean()).isFalse();
        when(clock.instant()).thenReturn(Instant.parse("2026-11-30T17:00:00Z"));
        assertThat(month(managerToken,"2026-11").path("finalized").asBoolean()).isTrue();
    }
    @Test void managersAreScopedAndAdminCanSearchAllEmployeesWithPagination()throws Exception{
        var managed=month(managerToken,"2026-11");assertThat(managed.path("totalElements").asInt()).isEqualTo(2);
        assertThat(managed.path("items").get(1).path("score").asInt()).isEqualTo(100);
        assertThat(month(adminToken,"2026-11").path("totalElements").asInt()).isEqualTo(3);
        mvc.perform(get("/api/v1/diligence").header("Authorization","Bearer "+adminToken).param("month","2026-11").param("q","outside@example.com").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.items[0].employeeName").value("Outside"));
        mvc.perform(get("/api/v1/diligence").header("Authorization","Bearer "+adminToken).param("month","2026-11").param("q","%"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/diligence").header("Authorization","Bearer "+managerToken).param("month","2026-11").param("page","1").param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].employeeName").value("Binh")).andExpect(jsonPath("$.totalPages").value(2));
    }
    @Test void unauthorizedEmployeesAndOtherManagersCannotReadScores()throws Exception{
        mvc.perform(get("/api/v1/diligence")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/diligence").header("Authorization","Bearer "+employeeToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/diligence/employees/"+employee.getId()).header("Authorization","Bearer "+otherToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/diligence/employees/"+outside.getId()).header("Authorization","Bearer "+managerToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/diligence/employees/"+manager.getId()).header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
        assertThat(detail(adminToken,outside.getId(),"2026-11").at("/employee/score").asInt()).isEqualTo(100);
    }
    @Test void validatesMonthAndPageWithoutServerErrors()throws Exception{
        for(String value:List.of("2026-13","2026-1","2027-01","0000-01","bad"))
            mvc.perform(get("/api/v1/diligence").header("Authorization","Bearer "+managerToken).param("month",value)).andExpect(status().isBadRequest());
        for(String page:List.of("-1","2147483648"))mvc.perform(get("/api/v1/diligence").header("Authorization","Bearer "+managerToken).param("page",page)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/diligence").header("Authorization","Bearer "+managerToken).param("size","101")).andExpect(status().isBadRequest());
        assertThat(detail(managerToken,employee.getId(),"2026-11").path("items").size()).isZero();
    }
    @Test void overnightCheckInBelongsToShiftStartMonthAndOnlyConfirmedCheckInsDeduct()throws Exception{
        var start=Instant.parse("2026-10-31T16:55:00Z");var id=shift(employee.getId(),LocalDate.of(2026,10,31),start);
        event(id,employee.getId(),start.plusSeconds(600),EventType.CHECK_IN);event(id,employee.getId(),start.plusSeconds(8*3600),EventType.CHECK_OUT);
        assertThat(detail(managerToken,employee.getId(),"2026-10").at("/employee/score").asInt()).isEqualTo(95);
        assertThat(detail(managerToken,employee.getId(),"2026-11").at("/employee/score").asInt()).isEqualTo(100);
        shift(employee.getId(),LocalDate.of(2026,11,5),Instant.parse("2026-11-05T01:00:00Z"));
        assertThat(detail(managerToken,employee.getId(),"2026-11").at("/employee/score").asInt()).isEqualTo(80);
    }
    @Test void detailPaginationPreservesTotalScoreAndFloorIsZero()throws Exception{
        for(int day=1;day<=6;day++)late(day,35);
        mvc.perform(get("/api/v1/diligence/employees/"+employee.getId()).header("Authorization","Bearer "+managerToken).param("month","2026-11").param("page","1").param("size","2"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.employee.score").value(0)).andExpect(jsonPath("$.employee.deductedPoints").value(120))
            .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalElements").value(6)).andExpect(jsonPath("$.totalPages").value(3));
    }
    @Test void successfulAttendanceChangesScoreImmediatelyAndDuplicateOrRejectedUploadDoesNot()throws Exception{
        var now=Instant.now();when(clock.instant()).thenReturn(now.plusSeconds(120));
        var date=now.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();var month=YearMonth.from(date).toString();
        var id=shift(employee.getId(),date,now.minusSeconds(600));
        assertThat(detail(managerToken,employee.getId(),month).at("/employee/score").asInt()).isEqualTo(100);
        mvc.perform(upload(id,"5000").header("Authorization","Bearer "+employeeToken)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("GPS_ACCURACY_TOO_LOW"));
        assertThat(detail(managerToken,employee.getId(),month).at("/employee/score").asInt()).isEqualTo(100);
        mvc.perform(upload(id,"5").header("Authorization","Bearer "+employeeToken)).andExpect(status().isCreated());
        assertThat(detail(managerToken,employee.getId(),month).at("/employee/score").asInt()).isEqualTo(95);
        mvc.perform(upload(id,"5").header("Authorization","Bearer "+employeeToken)).andExpect(status().isConflict());
        assertThat(detail(managerToken,employee.getId(),month).at("/employee/checkInCount").asInt()).isEqualTo(1);
    }
    @Test void managerReviewRecalculatesScoreKeepsAuditAndRejectsStaleOrOutOfScopeChanges()throws Exception{
        var date=LocalDate.of(2026,11,8);shift(employee.getId(),date,Instant.parse("2026-11-08T01:00:00Z"));
        assertThat(detail(managerToken,employee.getId(),"2026-11").at("/employee/score").asInt()).isEqualTo(80);
        String url="/api/v1/diligence/employees/"+employee.getId()+"/days/"+date;
        var request=Map.of("absence","APPROVED_LEAVE","earlyLeaveApproved",false,"missing","AUTO","note","Approved annual leave document","revision",0);
        mvc.perform(put(url+"/review").header("Authorization","Bearer "+otherToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request))).andExpect(status().isForbidden());
        mvc.perform(put(url+"/review").header("Authorization","Bearer "+employeeToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request))).andExpect(status().isForbidden());
        mvc.perform(put(url+"/review").header("Authorization","Bearer "+managerToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        assertThat(detail(managerToken,employee.getId(),"2026-11").at("/employee/score").asInt()).isEqualTo(100);
        mvc.perform(get(url+"/review").header("Authorization","Bearer "+managerToken)).andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(1));
        mvc.perform(put(url+"/review").header("Authorization","Bearer "+managerToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DILIGENCE_REVIEW_CHANGED"));
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder upload(UUID id,String accuracy){
        var now=Instant.now().toString();return multipart("/api/v1/attendance/events")
            .file(new MockMultipartFile("photo","timemark.jpg","image/jpeg",new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,1}))
            .param("scheduleId",id.toString()).param("type","CHECK_IN").param("locatedAt",now).param("latitude","10.7726").param("longitude","106.6579")
            .param("accuracyMeters",accuracy).param("timemarkCapturedAt",now).param("timemarkLatitude","10.7726").param("timemarkLongitude","106.6579");
    }
}
