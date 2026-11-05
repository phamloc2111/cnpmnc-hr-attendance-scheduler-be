package com.company.hr.attendance.organization;
import java.util.UUID;
public interface PositionsApi {
    record Selection(UUID id, String name) {}
    /** An inactive selection can only be retained on the same existing record. */
    Selection select(UUID id, UUID currentId);
}
