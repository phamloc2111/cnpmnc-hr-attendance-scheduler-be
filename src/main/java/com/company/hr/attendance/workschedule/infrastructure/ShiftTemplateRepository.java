package com.company.hr.attendance.workschedule.infrastructure;
import com.company.hr.attendance.workschedule.domain.ShiftTemplate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, UUID> {}
