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
                       boolean editable, String revision, List<Template> shifts) {}
    public record Catalog(List<Template> items, int page, int size, long totalElements, int totalPages) {}
}
