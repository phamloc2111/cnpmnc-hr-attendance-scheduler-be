package com.company.hr.attendance.identity.api;

import com.company.hr.attendance.identity.domain.Account;
import com.company.hr.attendance.identity.domain.Role;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
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

    public record ChangePasswordRequest(
        @JsonAlias("oldPassword") @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {
        @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8")
        public boolean isPasswordWithinByteLimit() {
            return newPassword == null || newPassword.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
        @Override public String toString() { return "ChangePasswordRequest[REDACTED]"; }
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
