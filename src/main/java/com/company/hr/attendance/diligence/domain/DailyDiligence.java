package com.company.hr.attendance.diligence.domain;

import java.time.*;
import java.util.*;
import static com.company.hr.attendance.diligence.domain.DayReview.*;

/** Pure chronological scoring, including context before the requested month for absence streaks. */
public final class DailyDiligence {
    private DailyDiligence() {}
    public record Shift(UUID id, String name, Instant startsAt, Instant endsAt, Instant checkInAt, Instant checkOutAt, int breakMinutes) {
        public Shift(UUID id, String name, Instant startsAt, Instant endsAt, Instant checkInAt, Instant checkOutAt) {
            this(id,name,startsAt,endsAt,checkInAt,checkOutAt,0);
        }
        public long workedMinutes() {
            if(checkInAt==null || checkOutAt==null) return 0;
            return Math.max(0,Duration.between(checkInAt,checkOutAt).toMinutes()-breakMinutes);
        }
    }
    public record Day(LocalDate date,List<Shift> shifts,DayReview review) {}
    public record Component(String code,int points,UUID scheduleId) {}
    public record Result(LocalDate workDate,List<Shift> shifts,List<Component> components,int rawPoints,int points,
        boolean capped,boolean disciplinaryReferral,boolean pendingExplanation,Instant explanationDeadline,
        boolean absent,boolean missing,DayReview review) {}
    public static List<Result> calculate(List<Day> input,YearMonth month,Instant now,DiligencePolicy policy) {
        return calculate(input,month,now,policy,Set.of());
    }
    public static List<Result> calculate(List<Day> input,YearMonth month,Instant now,DiligencePolicy policy,Set<UUID> lateExemptions) {
        var days=input.stream().sorted(Comparator.comparing(Day::date)).toList();
        var result=new ArrayList<Result>();int absentOrdinal=0,missingOrdinal=0,streak=0;LocalDate previousAbsence=null;
        for(var day:days){
            var review=day.review();var components=new ArrayList<Component>();
            boolean inMonth=YearMonth.from(day.date()).equals(month);
            boolean ended=day.shifts().stream().allMatch(shift->!now.isBefore(shift.endsAt()));
            boolean missingIn=day.shifts().stream().anyMatch(shift->shift.checkInAt()==null && !now.isBefore(shift.endsAt()));
            boolean missingOut=day.shifts().stream().anyMatch(shift->shift.checkOutAt()==null && !now.isBefore(shift.endsAt()));
            boolean approvedAbsence=review.absence()==Absence.APPROVED_LEAVE || review.absence()==Absence.LATE_APPROVED;
            boolean confirmedMissing=review.missing()!=Missing.AUTO;
            boolean absent=ended && day.shifts().stream().allMatch(shift->shift.checkInAt()==null) && !confirmedMissing && !approvedAbsence;
            var end=day.shifts().stream().map(Shift::endsAt).max(Comparator.naturalOrder()).orElseThrow();
            var deadline=end.plus(Duration.ofHours(48));
            boolean disciplinary=false,pending=false;
            if(absent){
                streak=previousAbsence!=null && day.date().equals(previousAbsence.plusDays(1))?streak+1:1;
                previousAbsence=day.date();if(inMonth)absentOrdinal++;
                disciplinary=streak>=3;
                components.add(new Component(disciplinary?"DISCIPLINARY_REFERRAL":absentOrdinal<=1?"ABSENCE_FIRST":"ABSENCE_REPEATED",disciplinary?0:absentOrdinal<=1?20:30,null));
            }else{streak=0;previousAbsence=null;}
            if(ended && missingIn && approvedAbsence){
                components.add(new Component(review.absence()==Absence.APPROVED_LEAVE?"APPROVED_LEAVE":"LATE_APPROVED_LEAVE",review.absence()==Absence.APPROVED_LEAVE?0:5,null));
            }
            boolean missing=ended && (missingIn||missingOut) && !absent && !approvedAbsence;
            if(missing){
                if(review.missing()!=Missing.SYSTEM_CONFIRMED && inMonth)missingOrdinal++;
                int deduction=0;String code;
                switch(review.missing()){
                    case SYSTEM_CONFIRMED -> code="SYSTEM_CONFIRMED";
                    case TIMELY_EXPLANATION -> {deduction=missingOrdinal<=2?0:2;code=deduction==0?"MISSING_WARNING":"MISSING_REPEATED";}
                    case LATE_EXPLANATION -> {deduction=5;code="MISSING_LATE_EXPLANATION";}
                    default -> {pending=now.isBefore(deadline);deduction=pending?0:5;code=pending?"MISSING_PENDING":"MISSING_EXPIRED";}
                }
                components.add(new Component(code,deduction,null));
            }
            // A disciplinary referral replaces all point sanctions for that day, while preserving earlier days.
            if(!disciplinary)for(var shift:day.shifts()){
                if(shift.checkInAt()!=null){int points=policy.penalty(shift.startsAt(),shift.checkInAt());
                    if(points>0)components.add(new Component(lateExemptions.contains(shift.id())?"LATE_EXCUSED":"LATE",lateExemptions.contains(shift.id())?0:points,shift.id()));}
                if(shift.checkOutAt()!=null){
                    var early=Duration.between(shift.checkOutAt(),shift.endsAt());
                    if(early.compareTo(Duration.ofMinutes(policy.graceMinutes()))>0){
                        int points=policy.earlyPenalty(shift.startsAt(),shift.endsAt(),shift.checkOutAt(),review.earlyLeaveApproved());
                        String code=review.earlyLeaveApproved()?"EARLY_APPROVED":early.multipliedBy(2).compareTo(Duration.between(shift.startsAt(),shift.endsAt()))>0?"HALF_DAY_ABSENCE":"EARLY_LEAVE";
                        components.add(new Component(code,points,shift.id()));
                    }
                }
            }
            int raw=components.stream().mapToInt(Component::points).sum(),points=Math.min(20,raw);
            if(inMonth)result.add(new Result(day.date(),day.shifts(),List.copyOf(components),raw,points,raw>20,disciplinary,pending,deadline,absent,missing,review));
        }
        return List.copyOf(result);
    }
}
