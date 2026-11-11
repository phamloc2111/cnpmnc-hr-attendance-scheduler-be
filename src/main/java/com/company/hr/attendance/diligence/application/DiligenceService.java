package com.company.hr.attendance.diligence.application;

import com.company.hr.attendance.attendance.AttendanceApi;
import com.company.hr.attendance.diligence.api.DiligenceModels.*;
import com.company.hr.attendance.diligence.DiligenceApi;
import com.company.hr.attendance.diligence.domain.*;
import com.company.hr.attendance.diligence.infrastructure.DayReviewRepository;
import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class DiligenceService implements DiligenceApi {
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final IdentityApi identity;private final WorkScheduleApi schedules;private final AttendanceApi attendance;
    private final DayReviewRepository reviews;private final Clock clock;private final DiligencePolicy policy;
    public DiligenceService(IdentityApi identity,WorkScheduleApi schedules,AttendanceApi attendance,DayReviewRepository reviews,
        @Qualifier("diligenceClock") Clock clock,@Value("${app.attendance.grace-minutes:5}") int graceMinutes){
        this.identity=identity;this.schedules=schedules;this.attendance=attendance;this.reviews=reviews;this.clock=clock;this.policy=new DiligencePolicy(graceMinutes);
    }
    @Override public List<DiligenceApi.Signal> signals(Collection<UUID> employeeIds, LocalDate from, LocalDate to) {
        if (employeeIds.isEmpty() || from.isAfter(to)) return List.of();
        var now = clock.instant();
        var result = new ArrayList<DiligenceApi.Signal>();
        var ids = List.copyOf(employeeIds);
        for (var month = YearMonth.from(from); !month.isAfter(YearMonth.from(to)); month = month.plusMonths(1)) {
            var monthly = entries(ids, month, now);
            for (var employeeId : ids) for (var day : monthly.getOrDefault(employeeId, List.of())) {
                if (day.workDate().isBefore(from) || day.workDate().isAfter(to)) continue;
                int eligible = 0, onTime = 0;
                for (var shift : day.shifts()) {
                    if (now.isBefore(shift.endsAt())) continue;
                    if (day.review().absence() == DayReview.Absence.APPROVED_LEAVE && shift.checkInAt() == null) continue;
                    eligible++;
                    if (shift.checkInAt() != null && !shift.checkInAt().isAfter(shift.startsAt().plus(Duration.ofMinutes(policy.graceMinutes())))) onTime++;
                }
                result.add(new DiligenceApi.Signal(employeeId, day.workDate(), day.absent(), eligible, onTime));
            }
        }
        return List.copyOf(result);
    }
    public MonthPage month(UUID actorId,String requestedMonth,String query,int page,int size){
        var now=clock.instant();var month=month(requestedMonth,now);var people=identity.employeePage(actorId,query,page,size);
        var entries=entries(people.stream().map(IdentityApi.Person::id).toList(),month,now);
        var rows=people.stream().map(person->summary(person,entries.getOrDefault(person.id(),List.of()))).toList();
        return new MonthPage(month.toString(),ZONE.getId(),finalized(month,now),now,policyView(),rows,page,size,people.getTotalElements(),people.getTotalPages());
    }
    public EmployeeMonth employee(UUID actorId,UUID employeeId,String requestedMonth,int page,int size){
        var actor=identity.person(actorId);if(actor.locked()||!Set.of("MANAGER","ADMIN").contains(actor.role()))throw new AccessDeniedException("Management access required");
        var person=identity.person(employeeId);if(!person.role().equals("EMPLOYEE")||(actor.role().equals("MANAGER")&&!actorId.equals(person.managerId())))throw new AccessDeniedException("Employee unavailable");
        var now=clock.instant();var month=month(requestedMonth,now);var entries=entries(List.of(employeeId),month,now).getOrDefault(employeeId,List.of());
        long offset=(long)page*size;var items=entries.stream().skip(offset).limit(size).toList();
        return new EmployeeMonth(month.toString(),ZONE.getId(),finalized(month,now),now,policyView(),summary(person,entries),items,page,size,entries.size(),(entries.size()+size-1)/size);
    }
    public OwnPeriod ownMonth(UUID actorId,String requestedMonth) {
        ownEmployee(actorId);
        var now=clock.instant();var month=month(requestedMonth,now);
        var days=entries(List.of(actorId),month,now).getOrDefault(actorId,List.of());
        return ownPeriod("MONTH",month.atDay(1),month.atEndOfMonth(),now,finalized(month,now),days);
    }
    public OwnPeriod ownWeek(UUID actorId,LocalDate selectedDate) {
        ownEmployee(actorId);
        var now=clock.instant();var today=now.atZone(ZONE).toLocalDate();
        if(selectedDate==null||selectedDate.isBefore(LocalDate.of(1900,1,1))||selectedDate.isAfter(today))
            throw new DiligenceException(HttpStatus.BAD_REQUEST,"INVALID_DILIGENCE_WEEK","Chọn ngày trong tuần hợp lệ, không ở tương lai.");
        var from=selectedDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var to=from.plusDays(6);
        var months=new LinkedHashSet<YearMonth>();months.add(YearMonth.from(from));months.add(YearMonth.from(to));
        var days=new ArrayList<DailyDiligence.Result>();
        for(var month:months)days.addAll(entries(List.of(actorId),month,now).getOrDefault(actorId,List.of()).stream()
            .filter(day->!day.workDate().isBefore(from)&&!day.workDate().isAfter(to)).toList());
        return ownPeriod("WEEK",from,to,now,to.isBefore(today),days);
    }
    private void ownEmployee(UUID actorId) {
        var actor=identity.person(actorId);
        if(actor.locked()||!actor.role().equals("EMPLOYEE"))throw new AccessDeniedException("Employee access required");
    }
    private OwnPeriod ownPeriod(String type,LocalDate from,LocalDate to,Instant now,boolean finalized,List<DailyDiligence.Result> entries) {
        var days=entries.stream().sorted(Comparator.comparing(DailyDiligence.Result::workDate)).map(day->new OwnDay(
            day.workDate(),day.shifts().stream().mapToLong(DailyDiligence.Shift::workedMinutes).sum(),day.points(),
            day.rawPoints(),day.capped(),day.pendingExplanation(),day.disciplinaryReferral(),day.components())).toList();
        long deducted=days.stream().mapToLong(OwnDay::deductedPoints).sum();
        long worked=days.stream().mapToLong(OwnDay::workedMinutes).sum();
        return new OwnPeriod(type,from,to,ZONE.getId(),now,finalized,policy.score(deducted),deducted,worked,days);
    }
    private Map<UUID,List<DailyDiligence.Result>> entries(List<UUID> employees,YearMonth month,Instant now){
        if(employees.isEmpty())return Map.of();
        var from=month.atDay(1).minusDays(2);var to=month.atEndOfMonth();
        var shifts=schedules.employeeShifts(employees,from,to);
        var events=attendance.events(shifts.stream().map(WorkScheduleApi.Shift::id).toList()).stream()
            .filter(event->!event.receivedAt().isAfter(now))
            .collect(Collectors.groupingBy(AttendanceApi.Event::scheduleId));
        var reviewMap=reviews.find(employees,from,to);
        var approved=attendance.approvedExplanations(shifts.stream().map(WorkScheduleApi.Shift::id).toList());
        var lateExemptions=approved.stream().filter(row->row.category().equals("LATE")&&row.exemptLatePenalty())
            .map(AttendanceApi.ApprovedExplanation::scheduleId).collect(Collectors.toSet());
        var grouped=shifts.stream().collect(Collectors.groupingBy(WorkScheduleApi.Shift::employeeId));
        var result=new HashMap<UUID,List<DailyDiligence.Result>>();
        for(var employee:employees){
            var days=grouped.getOrDefault(employee,List.of()).stream().collect(Collectors.groupingBy(WorkScheduleApi.Shift::workDate));
            var input=new ArrayList<DailyDiligence.Day>();
            for(var day:days.entrySet()){
                var dailyShifts=day.getValue().stream().sorted(Comparator.comparing(WorkScheduleApi.Shift::startsAt)).map(shift->{
                    var scheduleEvents=events.getOrDefault(shift.id(),List.of());
                    var checkIn=scheduleEvents.stream().filter(event->event.type().equals("CHECK_IN")).map(AttendanceApi.Event::receivedAt).min(Comparator.naturalOrder()).orElse(null);
                    var checkOut=scheduleEvents.stream().filter(event->event.type().equals("CHECK_OUT")).map(AttendanceApi.Event::receivedAt).min(Comparator.naturalOrder()).orElse(null);
                    return new DailyDiligence.Shift(shift.id(),shift.name(),shift.startsAt(),shift.endsAt(),checkIn,checkOut,shift.breakMinutes());
                }).toList();
                var current=reviewMap.getOrDefault(new DayReviewRepository.Key(employee,day.getKey()),DayReview.empty(employee,day.getKey()));
                if(current.missing()==DayReview.Missing.AUTO){
                    var submitted=approved.stream().filter(row->row.employeeId().equals(employee)&&row.workDate().equals(day.getKey())
                        && row.category().startsWith("MISSING_")).map(AttendanceApi.ApprovedExplanation::submittedAt).min(Comparator.naturalOrder());
                    if(submitted.isPresent()){
                        var deadline=dailyShifts.stream().map(DailyDiligence.Shift::endsAt).max(Comparator.naturalOrder()).orElseThrow().plus(Duration.ofHours(48));
                        current=new DayReview(employee,day.getKey(),current.absence(),current.earlyLeaveApproved(),
                            submitted.get().isAfter(deadline)?DayReview.Missing.LATE_EXPLANATION:DayReview.Missing.TIMELY_EXPLANATION,
                            current.note(),current.revision(),current.reviewedBy(),current.reviewedAt());
                    }
                }
                input.add(new DailyDiligence.Day(day.getKey(),dailyShifts,current));
            }
            result.put(employee,DailyDiligence.calculate(input,month,now,policy,lateExemptions));
        }
        return result;
    }
    private Summary summary(IdentityApi.Person person,List<DailyDiligence.Result> entries){
        long deduction=entries.stream().mapToLong(DailyDiligence.Result::points).sum();int score=policy.score(deduction);
        long checkIns=entries.stream().flatMap(day->day.shifts().stream()).filter(shift->shift.checkInAt()!=null).count();
        long late=entries.stream().flatMap(day->day.components().stream()).filter(component->component.code().equals("LATE")||component.code().equals("LATE_EXCUSED")).count();
        return new Summary(person.id(),person.name(),person.locked(),score,deduction,checkIns,late,
            score>=DiligencePolicy.REWARD_THRESHOLD?"ELIGIBLE":"INELIGIBLE",
            entries.stream().filter(DailyDiligence.Result::absent).count(),entries.stream().filter(DailyDiligence.Result::missing).count(),
            entries.stream().filter(DailyDiligence.Result::pendingExplanation).count(),entries.stream().anyMatch(DailyDiligence.Result::disciplinaryReferral));
    }
    private Policy policyView(){return new Policy(100,80,policy.graceMinutes(),5,10,20,20,48,15);}
    private YearMonth month(String value,Instant now){
        var current=YearMonth.from(now.atZone(ZONE));if(value==null||value.isBlank())return current;
        try{if(!value.matches("[0-9]{4}-[0-9]{2}"))throw new DateTimeException("Invalid month");var parsed=YearMonth.parse(value);
            if(parsed.getYear()<1900||parsed.isAfter(current))throw new DateTimeException("Month out of range");return parsed;
        }catch(DateTimeException error){throw new DiligenceException(HttpStatus.BAD_REQUEST,"INVALID_DILIGENCE_MONTH","Chọn tháng hợp lệ từ năm 1900 đến tháng hiện tại.");}
    }
    private boolean finalized(YearMonth month,Instant now){return month.isBefore(YearMonth.from(now.atZone(ZONE)));}
}
