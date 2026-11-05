package com.company.hr.attendance.identity.api;

import com.company.hr.attendance.identity.domain.Account;
import com.company.hr.attendance.identity.domain.Role;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class AuthModels {
    private AuthModels() {}

    public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password
    ) {
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }

    public record UserResponse(UUID id, String email, String displayName, Role role) {
        public static UserResponse from(Account account) {
            return new UserResponse(account.getId(), account.getEmail(), account.getDisplayName(), account.getRole());
        }
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
        @Override public String toString() { return "LoginResponse[REDACTED]"; }
    }
}
