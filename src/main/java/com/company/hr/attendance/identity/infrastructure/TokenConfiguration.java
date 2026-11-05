package com.company.hr.attendance.identity.infrastructure;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration
public class TokenConfiguration {
    @Bean
    SecretKey jwtKey(@Value("${app.auth.jwt-secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            LoggerFactory.getLogger(TokenConfiguration.class).warn(
                "JWT_SECRET is unset: using a temporary signing key; tokens will expire on restart. Set JWT_SECRET for deployment.");
        } else if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, AccountRepository accounts, LoginSessionRepository sessions,
                          @Value("${app.auth.issuer}") String issuer) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> activeSession = jwt -> {
            try {
                var session = sessions.findById(UUID.fromString(jwt.getId()));
                var account = accounts.findById(UUID.fromString(jwt.getSubject()));
                if (jwt.getExpiresAt() != null && jwt.getExpiresAt().isAfter(Instant.now())
                    && session.isPresent() && account.isPresent() && !account.get().isLocked()
                    && session.get().getAccountId().equals(account.get().getId())
                    && session.get().getExpiresAt().isAfter(Instant.now())
                    && account.get().getRole().name().equals(jwt.getClaimAsString("role"))) {
                    return OAuth2TokenValidatorResult.success();
                }
            } catch (IllegalArgumentException | NullPointerException ignored) {
                // Malformed subject/jti claims are rejected as an invalid bearer token.
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), activeSession));
        return decoder;
    }
}
