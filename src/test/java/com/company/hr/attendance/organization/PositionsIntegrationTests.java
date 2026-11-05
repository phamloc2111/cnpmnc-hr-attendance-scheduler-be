package com.company.hr.attendance.organization;

import com.company.hr.attendance.identity.PostgresTestConfig;
import com.company.hr.attendance.identity.domain.*;
import com.company.hr.attendance.identity.infrastructure.*;
import com.company.hr.attendance.organization.domain.PositionName;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class PositionsIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    Account admin, manager, managerB, employee;
    String adminToken, managerToken, otherToken, employeeToken;
    static final String PASSWORD = "Test-password-123!";

    @BeforeEach void seed() throws Exception {
        jdbc.update("delete from work_schedules"); jdbc.update("delete from work_shift_templates");
        sessions.deleteAll(); jdbc.update("delete from identity_account_skills"); jdbc.update("delete from identity_accounts");
        jdbc.update("delete from organization_positions");
        admin=save("admin",Role.ADMIN,null); manager=save("manager",Role.MANAGER,null);
        managerB=save("other-manager",Role.MANAGER,null); employee=save("employee",Role.EMPLOYEE,manager.getId());
        adminToken=token(admin); managerToken=token(manager); otherToken=token(managerB); employeeToken=token(employee);
    }
    Account save(String name,Role role,UUID managerId) {
        var account=new Account(name+"@example.com",passwords.encode(PASSWORD),name,role);
        account.updateDetails(account.getEmail(),name,role,null,List.of(),managerId);
        return accounts.saveAndFlush(account);
    }
    String token(Account account) throws Exception {
        return json(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email",account.getEmail(),"password",PASSWORD))))
            .andExpect(status().isOk())).get("accessToken").asText();
    }
    JsonNode json(ResultActions action) throws Exception { return mapper.readTree(action.andReturn().getResponse().getContentAsString()); }
    ResultActions create(String token,String name) throws Exception {
        return mvc.perform(post("/api/v1/positions").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("name",name))));
    }
    JsonNode position() throws Exception { return json(create(managerToken,"Thu ngân").andExpect(status().isCreated())); }
    ResultActions changeStatus(String token,JsonNode position,boolean active) throws Exception {
        return mvc.perform(patch("/api/v1/positions/"+position.get("id").asText()+"/status")
            .header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("active",active,"version",position.get("version").asLong()))));
    }
    Map<String,Object> accountBody(String email,String positionId) {
        var body=new HashMap<String,Object>();
        body.put("email",email); body.put("displayName","New employee"); body.put("password",PASSWORD);
        body.put("role","EMPLOYEE"); body.put("skills",List.of()); body.put("positionId",positionId);
        return body;
    }
    ResultActions newAccount(String positionId,String email) throws Exception {
        return mvc.perform(post("/api/v1/accounts").header("Authorization","Bearer "+adminToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(accountBody(email,positionId))));
    }
    ResultActions newShift(String positionId) throws Exception {
        return mvc.perform(post("/api/v1/shift-templates").header("Authorization","Bearer "+adminToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of(
                "name","Ca sáng","startTime","08:00","endTime","17:00","breakMinutes",60,
                "overnight",false,"requiredHeadcount",1,"requiredPositionId",positionId))));
    }
    @Test void create_manager_normalizesAndSharesWithAllManagement() throws Exception {
        var p=json(create(managerToken,"  Thu   ngân  ").andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Thu ngân")));
        for(var token:List.of(adminToken,otherToken)) {
            mvc.perform(get("/api/v1/positions").header("Authorization","Bearer "+token).param("active","true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(p.get("id").asText()));
        }
        newAccount(p.get("id").asText(),"new@example.com").andExpect(status().isCreated())
            .andExpect(jsonPath("$.position").value("Thu ngân")).andExpect(jsonPath("$.positionId").value(p.get("id").asText()));
        newShift(p.get("id").asText()).andExpect(status().isCreated())
            .andExpect(jsonPath("$.requiredPosition").value("Thu ngân"))
            .andExpect(jsonPath("$.requiredPositionId").value(p.get("id").asText()));
    }
    @Test void create_adminAndEmployee_cannotWriteCatalog() throws Exception {
        var p=position();
        for(var token:List.of(adminToken,employeeToken)) {
            create(token,"Pha chế").andExpect(status().isForbidden());
            changeStatus(token,p,false).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/positions").header("Authorization","Bearer "+employeeToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/positions")).andExpect(status().isUnauthorized());
    }
    @Test void create_caseWhitespaceAndUnicodeDuplicates_returns409IncludingInactive() throws Exception {
        var p=position();
        create(otherToken,"  THU   NGÂN  ").andExpect(status().isConflict());
        create(otherToken,"Thu nga\u0302n").andExpect(status().isConflict());
        changeStatus(managerToken,p,false).andExpect(status().isOk());
        create(otherToken,"thu ngân").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from organization_positions",Integer.class)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"","   ","\u00a0"})
    void create_blankName_returns400(String name) throws Exception {
        create(managerToken,name).andExpect(status().isBadRequest());
    }
    @Test void inactivePosition_preservesExistingButPreventsNewAssignments() throws Exception {
        var p=position(); var id=p.get("id").asText();
        var existing=json(newAccount(id,"existing@example.com").andExpect(status().isCreated()));
        var shift=json(newShift(id).andExpect(status().isCreated()));
        changeStatus(otherToken,p,false).andExpect(status().isOk());
        newAccount(id,"new@example.com").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POSITION_INACTIVE"));
        newShift(id).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("POSITION_INACTIVE"));
        var body=accountBody("existing@example.com",id);body.put("version",existing.get("version").asLong());body.put("displayName","Edited employee");
        mvc.perform(put("/api/v1/accounts/"+existing.get("id").asText()).header("Authorization","Bearer "+adminToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.position").value("Thu ngân"));
        mvc.perform(get("/api/v1/shift-templates").header("Authorization","Bearer "+adminToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(shift.get("id").asText()))
            .andExpect(jsonPath("$.items[0].requiredPosition").value("Thu ngân"));
        mvc.perform(get("/api/v1/positions").header("Authorization","Bearer "+adminToken).param("active","true"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/positions/"+id).header("Authorization","Bearer "+adminToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }
    @Test void status_reactivationAndStaleVersion_checked() throws Exception {
        var p=position();
        var stopped=json(changeStatus(managerToken,p,false).andExpect(status().isOk()));
        changeStatus(managerToken,p,true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_POSITION"));
        changeStatus(otherToken,stopped,true).andExpect(status().isOk());
        newAccount(p.get("id").asText(),"new@example.com").andExpect(status().isCreated());
    }
    @Test void unknownPositionAndFreeText_cannotBypassCatalog() throws Exception {
        var unknown=UUID.randomUUID().toString();
        newAccount(unknown,"new@example.com").andExpect(status().isNotFound());
        newShift(unknown).andExpect(status().isNotFound());
        var body=accountBody("new@example.com",null);body.put("position","Arbitrary position");
        mvc.perform(post("/api/v1/accounts").header("Authorization","Bearer "+adminToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
    }
    @Test void list_searchFiltersAndPagination_areServerSide() throws Exception {
        create(managerToken,"Thu ngân").andExpect(status().isCreated());
        create(managerToken,"Pha chế").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/positions").header("Authorization","Bearer "+adminToken)
            .param("size","1").param("page","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.items[0].name").value("Thu ngân"));
        mvc.perform(get("/api/v1/positions").header("Authorization","Bearer "+adminToken).param("q","THU  NGÂN"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void migration_v5TextPositions_backfillsSharedIdsWithoutLosingLabels() {
        var schema="positions_upgrade";
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).target("5").load().migrate();
            var oldAccount=UUID.randomUUID(); var oldShift=UUID.randomUUID();
            jdbc.update("insert into positions_upgrade.identity_accounts(id,email,password_hash,display_name,role,locked,created_at,version,position) values(?,?,?,?,?,false,current_timestamp,0,?)",
                oldAccount,"legacy@example.com","test-only-hash","Legacy","EMPLOYEE"," Thu   ngân ");
            jdbc.update("insert into positions_upgrade.work_shift_templates(id,name,start_time,end_time,break_minutes,created_by,required_headcount,required_position,overnight) values(?,?,cast('08:00' as time),cast('17:00' as time),60,?,1,?,false)",
                oldShift,"Legacy shift",manager.getId(),"thu ngân");
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(jdbc.queryForObject("select count(*) from positions_upgrade.organization_positions",Integer.class)).isEqualTo(1);
            var accountPosition=jdbc.queryForObject("select position_id from positions_upgrade.identity_accounts where id=?",UUID.class,oldAccount);
            var shiftPosition=jdbc.queryForObject("select required_position_id from positions_upgrade.work_shift_templates where id=?",UUID.class,oldShift);
            assertThat(accountPosition).isNotNull().isEqualTo(shiftPosition);
            assertThat(jdbc.queryForObject("select position from positions_upgrade.identity_accounts where id=?",String.class,oldAccount)).isEqualTo(" Thu   ngân ");
        } finally { jdbc.execute("drop schema if exists positions_upgrade cascade"); }
    }
}
