package com.company.hr.attendance.organization.infrastructure;
import com.company.hr.attendance.organization.domain.Position;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;

public interface PositionRepository extends JpaRepository<Position, UUID>, JpaSpecificationExecutor<Position> {
    boolean existsByNormalizedName(String name);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from Position p where p.id = :id")
    Optional<Position> lockForSelection(UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Position p where p.id = :id")
    Optional<Position> lockForChange(UUID id);
}
