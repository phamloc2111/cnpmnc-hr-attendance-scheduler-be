package com.company.hr.attendance.diligence.domain;
import java.time.*;
import java.util.UUID;

/** Manager-verified facts, never an arbitrary number of points. */
public record DayReview(UUID employeeId, LocalDate workDate, Absence absence, boolean earlyLeaveApproved,
    Missing missing, String note, long revision, UUID reviewedBy, Instant reviewedAt) {
    public enum Absence { AUTO, APPROVED_LEAVE, LATE_APPROVED, UNEXCUSED }
    public enum Missing { AUTO, TIMELY_EXPLANATION, LATE_EXPLANATION, SYSTEM_CONFIRMED }
    public static DayReview empty(UUID employee,LocalDate date){return new DayReview(employee,date,Absence.AUTO,false,Missing.AUTO,"",0,null,null);}
}
