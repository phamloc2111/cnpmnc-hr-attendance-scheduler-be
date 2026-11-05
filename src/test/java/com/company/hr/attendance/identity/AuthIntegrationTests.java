package com.company.hr.attendance.identity;

import com.company.hr.attendance.identity.domain.*;
import com.company.hr.attendance.identity.infrastructure.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountRepository accounts;
    @Autowired LoginSessionRepository sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired SessionCleanup cleanup;
    private static final String PASSWORD = "Test-password-123!";

    @BeforeEach
    void setup() {
        sessions.deleteAll();
        accounts.deleteAll();
        for (var role : Role.values()) {
            accounts.save(new Account(role.name().toLowerCase() + "@example.com",
                passwords.encode(PASSWORD), "Test " + role, role));
        }
    }

    private String login(String email) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("tokenType").value("Bearer"))
            .andExpect(jsonPath("user.passwordHash").doesNotExist()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @ParameterizedTest
    @CsvSource({"employee,EMPLOYEE", "manager,MANAGER", "admin,ADMIN"})
    void login_validAccount_returnsJwtAndCurrentUser(String email, String role) throws Exception {
        var token = login(email + "@example.com");
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("role").value(role))
            .andExpect(jsonPath("email").value(email + "@example.com"));
        assertThat(accounts.findByEmail(email + "@example.com").orElseThrow().getPasswordHash())
            .startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void login_uppercaseEmail_normalizesEmail() throws Exception {
        login("ADMIN@EXAMPLE.COM");
    }

    @ParameterizedTest
    @CsvSource({"employee@example.com,wrong", "missing@example.com,wrong"})
    void login_invalidCredentials_returnsSameMessage(String email, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("message").value("Email hoặc mật khẩu không đúng"));
        assertThat(sessions.count()).isZero();
    }

    @Test
    void login_lockedAccount_deniesLogin() throws Exception {
        var account = accounts.findByEmail("employee@example.com").orElseThrow();
        account.setLocked(true);
        accounts.save(account);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", account.getEmail(), "password", PASSWORD))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("message").value("Tài khoản đã bị khóa"));
    }

    @Test
    void login_invalidPayload_returnsValidationError() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\",\"password\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @CsvSource({
        "employee,employee,200", "employee,management,403", "employee,administration,403",
        "manager,employee,403", "manager,management,200", "manager,administration,403",
        "admin,employee,403", "admin,management,200", "admin,administration,200"
    })
    void workspace_eachRole_enforcesPermission(String role, String workspace, int expectedStatus) throws Exception {
        var token = login(role + "@example.com");
        mvc.perform(get("/api/v1/workspaces/" + workspace).header("Authorization", "Bearer " + token))
            .andExpect(status().is(expectedStatus));
    }

    @Test
    void logout_currentSession_revokesOnlyThatToken() throws Exception {
        var first = login("employee@example.com");
        var second = login("employee@example.com");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + first))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + first))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + second))
            .andExpect(status().isOk());
    }

    @Test
    void me_accountLockedAfterLogin_rejectsExistingToken() throws Exception {
        var token = login("employee@example.com");
        var account = accounts.findByEmail("employee@example.com").orElseThrow();
        account.setLocked(true);
        accounts.save(account);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void me_roleChangedAfterLogin_rejectsOldPrivileges() throws Exception {
        var token = login("admin@example.com");
        var account = accounts.findByEmail("admin@example.com").orElseThrow();
        account.setRole(Role.EMPLOYEE);
        accounts.save(account);
        mvc.perform(get("/api/v1/workspaces/administration").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void me_expiredJwt_rejectsEvenIfSessionIsActive() throws Exception {
        var account = accounts.findByEmail("employee@example.com").orElseThrow();
        var session = sessions.save(new LoginSession(account.getId(), Instant.now().plusSeconds(300)));
        var claims = JwtClaimsSet.builder().issuer("hr-attendance").subject(account.getId().toString())
            .id(session.getId().toString()).issuedAt(Instant.now().minusSeconds(600))
            .expiresAt(Instant.now().minusSeconds(120)).claim("role", "EMPLOYEE").build();
        var token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token.getTokenValue()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void me_forgedRole_rejectsToken() throws Exception {
        var token = login("employee@example.com");
        var parts = token.split("\\.");
        var payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
        parts[1] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            payload.replace("EMPLOYEE", "ADMIN").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/api/v1/workspaces/administration").header("Authorization", "Bearer " + String.join(".", parts)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedApi_missingOrMalformedToken_returnsJson401() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void cleanup_expiredSessions_removesOnlyExpiredSessions() {
        var account = accounts.findByEmail("employee@example.com").orElseThrow();
        sessions.save(new LoginSession(account.getId(), Instant.now().minusSeconds(60)));
        var active = sessions.save(new LoginSession(account.getId(), Instant.now().plusSeconds(60)));
        cleanup.removeExpiredSessions();
        assertThat(sessions.findAll()).extracting(LoginSession::getId).containsExactly(active.getId());
    }

    @Test
    void cors_preflight_allowsConfiguredWebOrigin() throws Exception {
        mvc.perform(options("/api/v1/auth/me").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
