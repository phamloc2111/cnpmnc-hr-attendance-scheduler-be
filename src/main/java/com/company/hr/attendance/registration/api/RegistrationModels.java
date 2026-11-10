package com.company.hr.attendance.registration.api;

import com.company.hr.attendance.workschedule.ShiftCatalogApi.Template;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class RegistrationModels {
    private RegistrationModels() {}
    public record Save(@NotNull LocalDate weekStart, @NotNull @Size(max = 100) List<@NotNull UUID> shiftIds,
                       @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String revision) {}
    public record Week(LocalDate weekStart, LocalDate weekEnd, Instant serverNow, Instant opensAt, Instant closesAt,
                       boolean editable, boolean reopened, String revision, List<Template> shifts) {}
    public record AccessChange(@NotNull LocalDate weekStart, @NotNull Boolean reopened,
                               @NotBlank @Size(max = 36) String accessRevision) {}
    public record ManagedEmployee(UUID employeeId, String displayName, boolean accountLocked, int selectionCount,
                                  String state, Instant reopenedAt, String accessRevision, boolean canReopen, boolean canClose) {}
    public record ManagedWeek(LocalDate weekStart, LocalDate weekEnd, Instant serverNow, Instant opensAt, Instant closesAt,
                              List<ManagedEmployee> items, int page, int size, long totalElements, int totalPages) {}
    public record Catalog(List<Template> items, int page, int size, long totalElements, int totalPages) {}
}
