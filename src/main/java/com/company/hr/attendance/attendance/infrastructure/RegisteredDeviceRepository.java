package com.company.hr.attendance.attendance.infrastructure;

import com.company.hr.attendance.attendance.domain.RegisteredDevice;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegisteredDeviceRepository extends JpaRepository<RegisteredDevice, UUID> {
    List<RegisteredDevice> findByEmployeeIdIn(Collection<UUID> employeeIds);
}
