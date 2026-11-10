package com.company.hr.attendance.workschedule;

import java.time.LocalTime;
import java.util.*;
import org.springframework.data.domain.Page;

/** Read-only shift template catalog for registration; no persistence entities cross module boundaries. */
public interface ShiftCatalogApi {
    record Template(UUID id, String name, LocalTime startTime, LocalTime endTime, int breakMinutes,
                    int workingMinutes, boolean overnight, String requiredPosition) {}
    Page<Template> page(int page, int size);
    List<Template> find(Collection<UUID> ids);
}
