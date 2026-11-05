package com.company.hr.attendance.identity.api;

import com.company.hr.attendance.identity.domain.Account;
import com.company.hr.attendance.identity.domain.Role;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public final class AccountModels {
    private AccountModels() {}

    public record CreateRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 120) String displayName,
        @NotNull Role role,
        @Size(max = 120) String position,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 80) String> skills,
        UUID managerId
    ) {
        @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8")
        public boolean isPasswordWithinByteLimit() {
            return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
        }
        @Override public String toString() { return "CreateAccountRequest[REDACTED]"; }
    }

    public record UpdateRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 120) String displayName,
        @NotNull Role role,
        @Size(max = 120) String position,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 80) String> skills,
        UUID managerId,
        @NotNull @PositiveOrZero Long version
    ) {}

    public record LockRequest(@NotNull Boolean locked, @NotNull @PositiveOrZero Long version) {}

    public record AccountResponse(UUID id, String email, String displayName, Role role, boolean locked,
                                  String position, List<String> skills, UUID managerId, long version) {
        public static AccountResponse from(Account account) {
            return new AccountResponse(account.getId(), account.getEmail(), account.getDisplayName(),
                account.getRole(), account.isLocked(), account.getPosition(), List.copyOf(account.getSkills()),
                account.getManagerId(), account.getVersion());
        }
    }

    public record AccountPage(List<AccountResponse> items, int page, int size, long totalElements, int totalPages) {
        public static AccountPage from(Page<Account> page) {
            return new AccountPage(page.getContent().stream().map(AccountResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
