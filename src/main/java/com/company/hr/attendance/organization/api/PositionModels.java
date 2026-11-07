package com.company.hr.attendance.organization.api;
import com.company.hr.attendance.organization.domain.Position;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.data.domain.Page;
public final class PositionModels {
    private PositionModels() {}
    public record CreatePosition(@NotBlank @Size(max = 120) String name) {}
    public record SetStatus(@NotNull Boolean active, @NotNull @PositiveOrZero Long version) {}
    public record PositionResponse(UUID id, String name, boolean active, long version) {
        public static PositionResponse from(Position p) { return new PositionResponse(p.getId(),p.getName(),p.isActive(),p.getVersion()); }
    }
    public record PositionPage(List<PositionResponse> items,int page,int size,long totalElements,int totalPages) {
        public static PositionPage from(Page<Position> p) {
            return new PositionPage(p.map(PositionResponse::from).getContent(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages());
        }
    }
}
