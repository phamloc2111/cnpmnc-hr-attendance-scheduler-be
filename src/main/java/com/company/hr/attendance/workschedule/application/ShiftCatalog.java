package com.company.hr.attendance.workschedule.application;

import com.company.hr.attendance.workschedule.ShiftCatalogApi;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import com.company.hr.attendance.workschedule.domain.ShiftWindow;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ShiftCatalog implements ShiftCatalogApi {
    private final ShiftTemplateRepository repository;
    public ShiftCatalog(ShiftTemplateRepository repository) { this.repository = repository; }
    public Page<Template> page(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, Sort.by("name", "id"))).map(this::view);
    }
    public List<Template> find(Collection<UUID> ids) { return repository.findAllById(ids).stream().map(this::view).toList(); }
    private Template view(ShiftTemplate shift) {
        return new Template(shift.getId(), shift.getName(), shift.getStartTime(), shift.getEndTime(), shift.getBreakMinutes(),
            ShiftWindow.durationMinutes(shift.getStartTime(), shift.getEndTime()) - shift.getBreakMinutes(),
            shift.isOvernight(), shift.getRequiredPosition());
    }
}
