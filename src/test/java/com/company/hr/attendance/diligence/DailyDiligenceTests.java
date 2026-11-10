package com.company.hr.attendance.diligence;
import com.company.hr.attendance.diligence.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DailyDiligenceTests {
    static final YearMonth MONTH=YearMonth.of(2026,11);
    static final DiligencePolicy POLICY=new DiligencePolicy(5);
    static final Instant NOW=Instant.parse("2026-12-01T01:00:00Z");
    static final UUID EMPLOYEE=UUID.randomUUID();
    DailyDiligence.Day day(int date,Instant checkIn,Instant checkOut,DayReview.Absence absence,DayReview.Missing missing,boolean earlyApproved){
        var workDate=LocalDate.of(2026,11,date);var start=workDate.atTime(8,0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        return new DailyDiligence.Day(workDate,List.of(new DailyDiligence.Shift(UUID.randomUUID(),"Morning",start,start.plus(Duration.ofHours(8)),checkIn,checkOut)),
            new DayReview(EMPLOYEE,workDate,absence,earlyApproved,missing,"reviewed facts",1,UUID.randomUUID(),NOW));
    }
    DailyDiligence.Day day(int date,Instant checkIn,Instant checkOut){return day(date,checkIn,checkOut,DayReview.Absence.AUTO,DayReview.Missing.AUTO,false);}
    DailyDiligence.Result one(DailyDiligence.Day day,Instant now){return DailyDiligence.calculate(List.of(day),MONTH,now,POLICY).getFirst();}
    @Test void absenceIsCappedAndThirdConsecutiveDayIsReferredWithoutMorePoints(){
        var results=DailyDiligence.calculate(List.of(day(1,null,null),day(2,null,null),day(3,null,null),day(4,null,null)),MONTH,NOW,POLICY);
        assertThat(results).extracting(DailyDiligence.Result::points).containsExactly(20,20,0,0);
        assertThat(results).extracting(DailyDiligence.Result::rawPoints).containsExactly(20,30,0,0);
        assertThat(results.get(1).capped()).isTrue();assertThat(results.get(2).disciplinaryReferral()).isTrue();
        assertThat(POLICY.score(results.stream().mapToInt(DailyDiligence.Result::points).sum())).isEqualTo(60);
        assertThat(POLICY.score(20)).isEqualTo(DiligencePolicy.REWARD_THRESHOLD);
    }
    @Test void approvedLeaveAndApprovedLateNoticeUseFactBasedRates(){
        assertThat(one(day(1,null,null,DayReview.Absence.APPROVED_LEAVE,DayReview.Missing.AUTO,false),NOW).points()).isZero();
        assertThat(one(day(2,null,null,DayReview.Absence.LATE_APPROVED,DayReview.Missing.AUTO,false),NOW).points()).isEqualTo(5);
    }
    @Test void earlyDepartureRespectsFiveMinuteGraceTiersHalfShiftAndApproval(){
        var start=LocalDate.of(2026,11,1).atTime(8,0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        var end=start.plus(Duration.ofHours(8));
        assertThat(POLICY.earlyPenalty(start,end,end.minus(Duration.ofMinutes(5)),false)).isZero();
        assertThat(POLICY.earlyPenalty(start,end,end.minus(Duration.ofMinutes(15)),false)).isEqualTo(5);
        assertThat(POLICY.earlyPenalty(start,end,end.minus(Duration.ofMinutes(30)),false)).isEqualTo(10);
        assertThat(POLICY.earlyPenalty(start,end,end.minus(Duration.ofMinutes(31)),false)).isEqualTo(20);
        assertThat(POLICY.earlyPenalty(start,end,start.plus(Duration.ofMinutes(30)),false)).isEqualTo(15);
        assertThat(POLICY.earlyPenalty(start,end,start.plus(Duration.ofMinutes(30)),true)).isZero();
    }
    @Test void forgottenPunchWaitsFortyEightHoursThenDeductsFiveAndCountsOncePerDay(){
        var day=day(1,Instant.parse("2026-11-01T01:00:00Z"),null);
        var deadline=day.shifts().getFirst().endsAt().plus(Duration.ofHours(48));
        assertThat(one(day,deadline.minusNanos(1)).pendingExplanation()).isTrue();
        assertThat(one(day,deadline.minusNanos(1)).points()).isZero();
        assertThat(one(day,deadline).points()).isEqualTo(5);
        assertThat(one(day,deadline).missing()).isTrue();
    }
    @Test void firstTwoExplainedMissingDaysAreWarningsAndThirdCostsTwo(){
        var days=List.of(day(1,Instant.parse("2026-11-01T01:00:00Z"),null,DayReview.Absence.AUTO,DayReview.Missing.TIMELY_EXPLANATION,false),
            day(2,Instant.parse("2026-11-02T01:00:00Z"),null,DayReview.Absence.AUTO,DayReview.Missing.TIMELY_EXPLANATION,false),
            day(3,Instant.parse("2026-11-03T01:00:00Z"),null,DayReview.Absence.AUTO,DayReview.Missing.TIMELY_EXPLANATION,false));
        assertThat(DailyDiligence.calculate(days,MONTH,NOW,POLICY)).extracting(DailyDiligence.Result::points).containsExactly(0,0,2);
        assertThat(one(day(4,Instant.parse("2026-11-04T01:00:00Z"),null,DayReview.Absence.AUTO,DayReview.Missing.SYSTEM_CONFIRMED,false),NOW).points()).isZero();
    }
    @Test void lateAndEarlyTogetherNeverDeductMoreThanTwenty(){
        var day=day(1,Instant.parse("2026-11-01T01:35:00Z"),Instant.parse("2026-11-01T08:20:00Z"));
        var result=one(day,NOW);assertThat(result.rawPoints()).isEqualTo(40);assertThat(result.points()).isEqualTo(20);assertThat(result.capped()).isTrue();
    }
}
