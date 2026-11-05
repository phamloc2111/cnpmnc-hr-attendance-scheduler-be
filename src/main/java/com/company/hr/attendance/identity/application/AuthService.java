package com.company.hr.attendance.identity.application;

import com.company.hr.attendance.identity.api.AuthModels.*;
import com.company.hr.attendance.identity.domain.LoginSession;
import com.company.hr.attendance.identity.infrastructure.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AccountRepository accounts;
    private final LoginSessionRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final Duration ttl;
    private final String issuer;
    private final String dummyHash;

    public AuthService(AccountRepository accounts, LoginSessionRepository sessions, PasswordEncoder passwords,
                       JwtEncoder encoder, @Value("${app.auth.token-ttl}") Duration ttl,
                       @Value("${app.auth.issuer}") String issuer) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.passwords = passwords;
        this.encoder = encoder;
        this.ttl = ttl;
        this.issuer = issuer;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new LoginException(false);
        }
        var account = accounts.findByEmail(request.email().strip().toLowerCase(Locale.ROOT));
        var matches = passwords.matches(request.password(), account.map(a -> a.getPasswordHash()).orElse(dummyHash));
        if (!matches || account.isEmpty()) throw new LoginException(false);
        var user = account.orElseThrow();
        if (user.isLocked()) throw new LoginException(true);
        var now = Instant.now();
        var expires = now.plus(ttl);
        var session = sessions.save(new LoginSession(user.getId(), expires));
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(user.getId().toString())
            .issuedAt(now).expiresAt(expires).id(session.getId().toString())
            .claim("role", user.getRole().name()).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        var token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, "Bearer", expires, UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Jwt jwt) {
        return UserResponse.from(accounts.findById(UUID.fromString(jwt.getSubject())).orElseThrow());
    }

    @Transactional
    public void logout(Jwt jwt) {
        sessions.deleteById(UUID.fromString(jwt.getId()));
    }
}
