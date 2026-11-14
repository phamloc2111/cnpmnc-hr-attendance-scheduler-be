package com.company.hr.attendance.organization;
import java.util.UUID;
import java.util.Collection;
import java.util.Set;
public interface PositionsApi {
    record Selection(UUID id, String name) {}
    /** An inactive selection can only be retained on the same existing record. */
    Selection select(UUID id, UUID currentId);
    /** Active positions that may be used for new automatic assignments. */
    Set<UUID> activeIds(Collection<UUID> ids);
}
