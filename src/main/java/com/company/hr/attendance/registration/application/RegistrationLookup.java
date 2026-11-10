package com.company.hr.attendance.registration.application;
import com.company.hr.attendance.registration.RegistrationApi;
import com.company.hr.attendance.registration.infrastructure.RegistrationRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class RegistrationLookup implements RegistrationApi {
    private final RegistrationRepository repository;
    public RegistrationLookup(RegistrationRepository repository) { this.repository=repository; }
    public boolean hasRegistered(UUID employeeId,LocalDate weekStart) { return !repository.ids(employeeId,weekStart).isEmpty(); }
}
