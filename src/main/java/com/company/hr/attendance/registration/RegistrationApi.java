package com.company.hr.attendance.registration;
import java.time.LocalDate;
import java.util.UUID;
/** Public read boundary for reminder eligibility. Empty selections are not registered. */
public interface RegistrationApi { boolean hasRegistered(UUID employeeId, LocalDate weekStart); }
