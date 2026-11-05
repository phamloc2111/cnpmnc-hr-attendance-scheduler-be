package com.company.hr.attendance.identity;

import com.company.hr.attendance.identity.domain.*;
import com.company.hr.attendance.identity.infrastructure.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
class AccountManagementIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    Account admin, managerA, managerB, employeeA, employeeB;
    private static final String PASSWORD = "Test-password-123!";

    @BeforeEach
    void seed() {
        sessions.deleteAll();
        jdbc.update("DELETE FROM identity_account_skills");
        jdbc.update("DELETE FROM identity_accounts");
        admin = save("admin", Role.ADMIN, null);
        managerA = save("manager-a", Role.MANAGER, null);
        managerB = save("manager-b", Role.MANAGER, null);
        employeeA = save("employee-a", Role.EMPLOYEE, managerA.getId());
        employeeB = save("employee-b", Role.EMPLOYEE, managerB.getId());
    }

    private Account save(String name, Role role, UUID managerId) {
        var account = new Account(name + "@example.com", passwords.encode(PASSWORD), name, role);
        account.updateDetails(account.getEmail(), name, role, null, List.of(), managerId);
        return accounts.saveAndFlush(account);
    }

    private String token(Account account) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", account.getEmail(), "password", PASSWORD))))
            .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private Map<String, Object> details(Account account) {
        var body = new HashMap<String, Object>();
        body.put("email", account.getEmail());
        body.put("displayName", account.getDisplayName());
        body.put("role", account.getRole().name());
        body.put("skills", List.of("Thu ngân", "Pha chế"));
        body.put("position", "Thu ngân");
        body.put("managerId", account.getManagerId());
        body.put("version", account.getVersion());
        return body;
    }

    private JsonNode lock(String adminToken, Account account, boolean locked, long version) throws Exception {
        var result = mvc.perform(patch("/api/v1/accounts/" + account.getId() + "/lock")
            .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("locked", locked, "version", version))))
            .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void migration_realPostgres_runsV3AndValidatesSchema() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL");
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version = '3' and success", Integer.class))
            .isEqualTo(1);
    }

    @Test
    void create_manager_createsAssignedEmployeeWithHashedPasswordAndSkills() throws Exception {
        var body = details(employeeA);
        body.put("email", "NEW@example.com");
        body.put("password", PASSWORD);
        body.remove("managerId");
        var result = mvc.perform(post("/api/v1/accounts").header("Authorization", "Bearer " + token(managerA))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.email").value("new@example.com"))
            .andExpect(jsonPath("$.managerId").value(managerA.getId().toString()))
            .andExpect(jsonPath("$.skills[0]").value("Thu ngân"))
            .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn();
        var id = UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
        var saved = accounts.findById(id).orElseThrow();
        assertThat(passwords.matches(PASSWORD, saved.getPasswordHash())).isTrue();
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", "new@example.com", "password", PASSWORD))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("EMPLOYEE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "ADMIN"})
    void create_managerCannotGrantElevatedRoles_returns403(String role) throws Exception {
        var body = details(employeeA);
        body.put("email", "new@example.com"); body.put("password", PASSWORD); body.put("role", role);
        mvc.perform(post("/api/v1/accounts").header("Authorization", "Bearer " + token(managerA))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(accounts.findByEmail("new@example.com")).isEmpty();
    }

    @Test
    void update_managerCannotPromoteOwnEmployee_returns403() throws Exception {
        var body = details(employeeA); body.put("role", "ADMIN");
        mvc.perform(put("/api/v1/accounts/" + employeeA.getId()).header("Authorization", "Bearer " + token(managerA))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
        assertThat(accounts.findById(employeeA.getId()).orElseThrow().getRole()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void list_managerOnlySeesAssignedEmployees() throws Exception {
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token(managerA)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.items[0].id").value(employeeA.getId().toString()));
    }

    @Test
    void get_managerCannotReadOtherManagersEmployee_returns404() throws Exception {
        mvc.perform(get("/api/v1/accounts/" + employeeB.getId()).header("Authorization", "Bearer " + token(managerA)))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_managerCannotEditOtherManagersEmployee_returns404() throws Exception {
        mvc.perform(put("/api/v1/accounts/" + employeeB.getId()).header("Authorization", "Bearer " + token(managerA))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(details(employeeB))))
            .andExpect(status().isNotFound());
    }

    @Test
    void lock_managerCannotLockOtherManagersEmployee_returns404() throws Exception {
        mvc.perform(patch("/api/v1/accounts/" + employeeB.getId() + "/lock").header("Authorization", "Bearer " + token(managerA))
            .contentType(MediaType.APPLICATION_JSON).content("{\"locked\":true,\"version\":0}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void list_employeeOrAnonymous_denied() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token(employeeA)))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_duplicateEmailIgnoringCase_returns409() throws Exception {
        var body = details(employeeA); body.put("email", "EMPLOYEE-A@example.com"); body.put("password", PASSWORD);
        mvc.perform(post("/api/v1/accounts").header("Authorization", "Bearer " + token(admin))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
    }

    @Test
    void update_replacesSkillsAndChecksVersion() throws Exception {
        var body = details(employeeA); body.put("displayName", "Đã sửa");
        mvc.perform(put("/api/v1/accounts/" + employeeA.getId()).header("Authorization", "Bearer " + token(managerA))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Đã sửa"))
            .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.skills.length()").value(2));
        body.put("skills", List.of("Phục vụ")); body.put("version", 1);
        mvc.perform(put("/api/v1/accounts/" + employeeA.getId()).header("Authorization", "Bearer " + token(managerA))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.skills.length()").value(1))
            .andExpect(jsonPath("$.skills[0]").value("Phục vụ"));
        mvc.perform(patch("/api/v1/accounts/" + employeeA.getId() + "/lock").header("Authorization", "Bearer " + token(managerA))
            .contentType(MediaType.APPLICATION_JSON).content("{\"locked\":true,\"version\":0}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_ACCOUNT"));
        assertThat(accounts.findById(employeeA.getId()).orElseThrow().isLocked()).isFalse();
    }

    @Test
    void lockThenUnlock_revokesSessionsPermanently() throws Exception {
        var employeeToken = token(employeeA);
        var adminToken = token(admin);
        var locked = lock(adminToken, employeeA, true, 0);
        assertThat(locked.get("locked").asBoolean()).isTrue();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", employeeA.getEmail(), "password", PASSWORD))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        lock(adminToken, employeeA, false, locked.get("version").asLong());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isUnauthorized());
        token(employeeA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "ADMIN"})
    void update_adminCanChangeRoleAndOldTokenIsRevoked(String role) throws Exception {
        var employeeToken = token(employeeA);
        var body = details(employeeA); body.put("role", role); body.put("managerId", null);
        mvc.perform(put("/api/v1/accounts/" + employeeA.getId()).header("Authorization", "Bearer " + token(admin))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value(role))
            .andExpect(jsonPath("$.position").isEmpty()).andExpect(jsonPath("$.skills").isEmpty());
        assertThat(accounts.findById(employeeA.getId()).orElseThrow().getPosition()).isNull();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + employeeToken))
            .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MANAGER", "ADMIN"})
    void create_managementRoles_doNotStoreEmployeePositionOrSkills(String role) throws Exception {
        var body = details(employeeA);
        body.put("email", "new-management@example.com");
        body.put("password", PASSWORD);
        body.put("role", role);
        body.put("managerId", null);
        mvc.perform(post("/api/v1/accounts").header("Authorization", "Bearer " + token(admin))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value(role))
            .andExpect(jsonPath("$.position").isEmpty()).andExpect(jsonPath("$.skills").isEmpty());
        assertThat(accounts.findByEmail("new-management@example.com").orElseThrow().getPosition()).isNull();
    }

    @Test
    void update_adminCannotDemoteSelf_returns409() throws Exception {
        var body = details(admin); body.put("role", "EMPLOYEE");
        mvc.perform(put("/api/v1/accounts/" + admin.getId()).header("Authorization", "Bearer " + token(admin))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_ROLE_CHANGE"));
    }

    @Test
    void lock_adminCannotLockSelf_returns409() throws Exception {
        mvc.perform(patch("/api/v1/accounts/" + admin.getId() + "/lock").header("Authorization", "Bearer " + token(admin))
            .contentType(MediaType.APPLICATION_JSON).content("{\"locked\":true,\"version\":0}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SELF_LOCK"));
    }

    @Test
    void update_managerWithAssignedEmployees_cannotChangeRole() throws Exception {
        var body = details(managerA); body.put("role", "EMPLOYEE");
        mvc.perform(put("/api/v1/accounts/" + managerA.getId()).header("Authorization", "Bearer " + token(admin))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("MANAGER_HAS_EMPLOYEES"));
    }

    @Test
    void update_adminCanReassignEmployeeBetweenManagers() throws Exception {
        var body = details(employeeA); body.put("managerId", managerB.getId());
        mvc.perform(put("/api/v1/accounts/" + employeeA.getId()).header("Authorization", "Bearer " + token(admin))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.managerId").value(managerB.getId().toString()));
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token(managerA)))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void update_employeeCannotBeAssignedToAnotherEmployee() throws Exception {
        var body = details(employeeA); body.put("managerId", employeeB.getId());
        mvc.perform(put("/api/v1/accounts/" + employeeA.getId()).header("Authorization", "Bearer " + token(admin))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MANAGER"));
    }

    @Test
    void create_multibytePasswordExceedingBcryptLimit_returns400() throws Exception {
        var body = details(employeeA); body.put("email", "new@example.com"); body.put("password", "ặ".repeat(30));
        mvc.perform(post("/api/v1/accounts").header("Authorization", "Bearer " + token(admin))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest());
        assertThat(accounts.findByEmail("new@example.com")).isEmpty();
    }

    @Test
    void update_duplicateSkillsIgnoringCase_returns400() throws Exception {
        var body = details(employeeA); body.put("skills", List.of("Pha chế", "pha chế"));
        mvc.perform(put("/api/v1/accounts/" + employeeA.getId()).header("Authorization", "Bearer " + token(admin))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DUPLICATE_SKILL"));
    }

    @Test
    void update_reorderedSkills_areSavedWithoutUniqueConstraintFailure() throws Exception {
        var body = details(employeeA);
        var adminToken = token(admin);
        var first = mvc.perform(put("/api/v1/accounts/" + employeeA.getId()).header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isOk()).andReturn();
        body.put("version", mapper.readTree(first.getResponse().getContentAsString()).get("version").asLong());
        body.put("skills", List.of("Pha chế", "Thu ngân"));
        mvc.perform(put("/api/v1/accounts/" + employeeA.getId()).header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.skills[0]").value("Pha chế"));
    }

    @Test
    void update_demotedManagerCannotBecomeOwnManager() throws Exception {
        var idle = save("idle", Role.MANAGER, null);
        var body = details(idle);
        body.put("role", "EMPLOYEE"); body.put("managerId", idle.getId());
        mvc.perform(put("/api/v1/accounts/" + idle.getId()).header("Authorization", "Bearer " + token(admin))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MANAGER"));
    }

    @Test
    void update_managerCannotReassignEmployeeToAnotherManager() throws Exception {
        var body = details(employeeA); body.put("managerId", managerB.getId());
        mvc.perform(put("/api/v1/accounts/" + employeeA.getId()).header("Authorization", "Bearer " + token(managerA))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
            .andExpect(status().isForbidden());
    }

    @Test
    void list_filtersSearchAndPaginationWorkOnPostgres() throws Exception {
        var auth = "Bearer " + token(admin);
        mvc.perform(get("/api/v1/accounts").header("Authorization", auth).param("role", "EMPLOYEE").param("size", "1").param("page", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/v1/accounts").header("Authorization", auth).param("q", "EMPLOYEE-A@"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/accounts").header("Authorization", auth).param("q", "%"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/accounts").header("Authorization", auth).param("size", "101"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
