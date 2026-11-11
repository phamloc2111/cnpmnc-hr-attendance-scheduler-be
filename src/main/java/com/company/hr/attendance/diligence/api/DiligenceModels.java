package com.company.hr.attendance.diligence.api;
import java.time.*;
import java.util.*;
import com.company.hr.attendance.diligence.domain.DayReview;
import com.company.hr.attendance.diligence.domain.DailyDiligence;
import jakarta.validation.constraints.*;

public final class DiligenceModels {
    private DiligenceModels() {}
    public record Policy(int initialScore,int rewardThreshold,int graceMinutes,int under15Penalty,int under30Penalty,int from30Penalty,int dailyCap,int explanationHours,int halfDayPenalty) {}
    public record Summary(UUID employeeId,String employeeName,boolean accountLocked,int score,long deductedPoints,long checkInCount,long lateCount,String rewardStatus,long absenceCount,long missingCount,long pendingExplanationCount,boolean disciplinaryReferral) {}
    public record MonthPage(String month,String timezone,boolean finalized,Instant asOf,Policy policy,List<Summary> items,int page,int size,long totalElements,int totalPages) {}
    public record EmployeeMonth(String month,String timezone,boolean finalized,Instant asOf,Policy policy,Summary employee,List<DailyDiligence.Result> items,int page,int size,long totalElements,int totalPages) {}
    public record OwnDay(LocalDate workDate,long workedMinutes,int deductedPoints,int rawPoints,boolean capped,
        boolean pendingExplanation,boolean disciplinaryReferral,List<DailyDiligence.Component> components) {}
    public record OwnPeriod(String periodType,LocalDate from,LocalDate to,String timezone,Instant asOf,boolean finalized,
        int score,long deductedPoints,long workedMinutes,List<OwnDay> days) {}
    public record ReviewRequest(@NotNull DayReview.Absence absence,boolean earlyLeaveApproved,@NotNull DayReview.Missing missing,@NotBlank @Size(min=10,max=1000) String note,@Min(0) long revision) {}
    public record ReviewAudit(long revision,UUID actorId,String actorName,Instant at,DayReview snapshot) {}
    public record ReviewHistory(DayReview review,List<ReviewAudit> history) {}
}
