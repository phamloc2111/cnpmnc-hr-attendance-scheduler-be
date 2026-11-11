package com.company.hr.attendance.priority;

import com.company.hr.attendance.identity.PostgresTestConfig;
import com.company.hr.attendance.identity.domain.*;
import com.company.hr.attendance.identity.infrastructure.*;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Import(PostgresTestConfig.class)
class PriorityIntegrationTests {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate jdbc;
    @Autowired AccountRepository accounts; @Autowired LoginSessionRepository sessions; @Autowired PasswordEncoder encoder;
    @Autowired ShiftTemplateRepository templates;
    @MockitoBean(name="diligenceClock") Clock clock;
    Account manager,otherManager,admin,employee,otherEmployee;String managerToken,adminToken,employeeToken,otherToken;UUID template;
    @BeforeEach void setup() throws Exception {
        clear();when(clock.instant()).thenReturn(Instant.parse("2026-12-31T01:00:00Z"));
        manager=account("PriorityManager",Role.MANAGER,null);otherManager=account("PriorityOtherManager",Role.MANAGER,null);
        admin=account("PriorityAdmin",Role.ADMIN,null);
        employee=account("PriorityEmployee",Role.EMPLOYEE,manager.getId());otherEmployee=account("PriorityOtherEmployee",Role.EMPLOYEE,otherManager.getId());
        managerToken=login(manager);otherToken=login(otherManager);adminToken=login(admin);employeeToken=login(employee);
        template=templates.saveAndFlush(new ShiftTemplate("Morning",LocalTime.of(8,0),LocalTime.of(17,0),60,manager.getId(),1,null,null,false)).getId();
    }
    @AfterEach void cleanup(){clear();}
    void clear(){
        jdbc.update("DELETE FROM priority_ratings");jdbc.update("DELETE FROM priority_weights");
        jdbc.update("DELETE FROM notification_deliveries");jdbc.update("DELETE FROM notification_devices");
        jdbc.update("DELETE FROM shift_registrations");jdbc.update("DELETE FROM registration_reopens");
        jdbc.update("DELETE FROM diligence_review_audit");jdbc.update("DELETE FROM diligence_day_reviews");
        jdbc.update("DELETE FROM attendance_explanations");jdbc.update("DELETE FROM attendance_records");jdbc.update("DELETE FROM attendance_events");jdbc.update("DELETE FROM work_schedules");
        templates.deleteAll();sessions.deleteAll();jdbc.update("DELETE FROM identity_account_skills");jdbc.update("UPDATE identity_accounts SET manager_id=NULL");accounts.deleteAll();
    }
    Account account(String name,Role role,UUID managerId){var account=new Account(name.toLowerCase(Locale.ROOT)+"@example.com",encoder.encode("Test-password-123!"),name,role);
        account.updateDetails(account.getEmail(),name,role,null,List.of(),managerId);return accounts.saveAndFlush(account);}
    String login(Account account)throws Exception{return mapper.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("email",account.getEmail(),"password","Test-password-123!"))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("accessToken").asText();}
    void absent(LocalDate date){var start=date.atTime(8,0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        jdbc.update("INSERT INTO work_schedules(id,employee_id,shift_template_id,work_date,shift_name,starts_at,ends_at,break_minutes,timezone,created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
            UUID.randomUUID(),employee.getId(),template,date,"Morning",java.sql.Timestamp.from(start),java.sql.Timestamp.from(start.plusSeconds(8*3600)),0,"Asia/Ho_Chi_Minh",manager.getId());}
    JsonNode team(String token,String week)throws Exception{return mapper.readTree(mvc.perform(get("/api/v1/priority-scores").param("week",week).header("Authorization","Bearer "+token))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    @Test void weightsAndRatingsChangeScoreAndValidateOwnership()throws Exception{
        absent(LocalDate.of(2026,11,10));
        var before=team(managerToken,"2026-11-16");
        assertThat(before.at("/weights/punctuality").asInt()).isEqualTo(35);
        assertThat(before.at("/items/0/absencePenalty").asInt()).isEqualTo(5);
        assertThat(before.at("/items/0/unexcusedAbsences").asInt()).isEqualTo(1);
        assertThat(team(managerToken,"2026-12-14").at("/items/0/absencePenalty").asInt()).isZero();
        mvc.perform(put("/api/v1/priority-scores/weights").header("Authorization","Bearer "+managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"punctuality\":0,\"hourBalance\":0,\"tenure\":0,\"positionMatch\":0,\"managerRating\":100}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/v1/priority-scores/ratings/"+employee.getId()).header("Authorization","Bearer "+managerToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
            .andExpect(status().isNoContent());
        assertThat(team(managerToken,"2026-11-16").at("/items/0/score").asInt()).isEqualTo(95);
        mvc.perform(put("/api/v1/priority-scores/weights").header("Authorization","Bearer "+managerToken).contentType(MediaType.APPLICATION_JSON)
            .content("{\"punctuality\":35,\"hourBalance\":25,\"tenure\":15,\"positionMatch\":15,\"managerRating\":9}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/priority-scores/ratings/"+otherEmployee.getId()).header("Authorization","Bearer "+managerToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
            .andExpect(status().isForbidden());
    }
    @Test void employeeOnlySeesOwnScoreAndAdminSelectsManager()throws Exception{
        mvc.perform(get("/api/v1/priority-scores/me").header("Authorization","Bearer "+employeeToken).param("week","2026-11-16"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.score").isNumber()).andExpect(jsonPath("$.items").doesNotExist());
        mvc.perform(get("/api/v1/priority-scores").header("Authorization","Bearer "+employeeToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/priority-scores").header("Authorization","Bearer "+otherToken).param("managerId",manager.getId().toString()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/priority-scores").header("Authorization","Bearer "+adminToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/priority-scores").header("Authorization","Bearer "+adminToken).param("managerId",manager.getId().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].employeeId").value(employee.getId().toString()));
    }
}
