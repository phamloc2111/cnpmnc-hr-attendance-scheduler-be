package com.company.hr.attendance.diligence.application;

import com.company.hr.attendance.attendance.AttendanceApi;
import com.company.hr.attendance.diligence.api.DiligenceModels.*;
import com.company.hr.attendance.diligence.domain.DiligencePolicy;
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
@Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class DiligenceService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final IdentityApi identity;
    private final WorkScheduleApi schedules;
    private final AttendanceApi attendance;
    private final Clock clock;
    private final DiligencePolicy policy;
    public DiligenceService(IdentityApi identity, WorkScheduleApi schedules, AttendanceApi attendance,
        @Qualifier("diligenceClock") Clock clock, @Value("${app.attendance.grace-minutes:5}") int graceMinutes) {
        this.identity=identity;this.schedules=schedules;this.attendance=attendance;this.clock=clock;this.policy=new DiligencePolicy(graceMinutes);
    }
    public MonthPage month(UUID actorId, String requestedMonth, String query, int page, int size) {
        var now=clock.instant();var month=month(requestedMonth, now);
        var people=identity.employeePage(actorId,query,page,size);
        var entries=entries(people.stream().map(IdentityApi.Person::id).toList(),month,now);
        var rows=people.stream().map(person->summary(person,entries.getOrDefault(person.id(),List.of()))).toList();
        return new MonthPage(month.toString(),ZONE.getId(),finalized(month,now),now,policyView(),rows,page,size,people.getTotalElements(),people.getTotalPages());
    }
    public EmployeeMonth employee(UUID actorId, UUID employeeId, String requestedMonth, int page, int size) {
        var actor=identity.person(actorId);
        if(actor.locked() || !Set.of("MANAGER","ADMIN").contains(actor.role())) throw new AccessDeniedException("Management access required");
        var person=identity.person(employeeId);
        if(!person.role().equals("EMPLOYEE") || (actor.role().equals("MANAGER") && !actorId.equals(person.managerId())))
            throw new AccessDeniedException("Employee unavailable");
        var now=clock.instant();var month=month(requestedMonth,now);
        var entries=entries(List.of(employeeId),month,now).getOrDefault(employeeId,List.of());
        long offset=(long)page*size;
        var items=entries.stream().skip(offset).limit(size).toList();
        return new EmployeeMonth(month.toString(),ZONE.getId(),finalized(month,now),now,policyView(),summary(person,entries),items,page,size,entries.size(),(entries.size()+size-1)/size);
    }
    private Map<UUID,List<Entry>> entries(List<UUID> employees, YearMonth month, Instant now) {
        var shifts=schedules.employeeShifts(employees,month.atDay(1),month.atEndOfMonth());
        var checkIns=attendance.checkIns(shifts.stream().map(WorkScheduleApi.Shift::id).toList()).stream()
            .filter(event->!event.receivedAt().isAfter(now))
            .collect(Collectors.toMap(AttendanceApi.CheckIn::scheduleId,AttendanceApi.CheckIn::receivedAt));
        var result=new HashMap<UUID,List<Entry>>();
        for(var shift:shifts) {
            var in=checkIns.get(shift.id());if(in==null)continue;
            result.computeIfAbsent(shift.employeeId(),ignored->new ArrayList<>()).add(new Entry(shift.id(),shift.workDate(),shift.name(),shift.startsAt(),in,
                policy.lateSeconds(shift.startsAt(),in),policy.penalty(shift.startsAt(),in)));
        }
        return result;
    }
    private Summary summary(IdentityApi.Person person, List<Entry> entries) {
        long deduction=entries.stream().mapToLong(Entry::deductedPoints).sum();int score=policy.score(deduction);
        return new Summary(person.id(),person.name(),person.locked(),score,deduction,entries.size(),entries.stream().filter(entry->entry.deductedPoints()>0).count(),
            score>=DiligencePolicy.REWARD_THRESHOLD?"ELIGIBLE":"INELIGIBLE");
    }
    private Policy policyView() { return new Policy(100,90,policy.graceMinutes(),5,10,20); }
    private YearMonth month(String value,Instant now) {
        var current=YearMonth.from(now.atZone(ZONE));
        if(value==null || value.isBlank())return current;
        try {
            if(!value.matches("[0-9]{4}-[0-9]{2}"))throw new DateTimeException("Invalid month");
            var parsed=YearMonth.parse(value);
            if(parsed.getYear()<1900 || parsed.isAfter(current))throw new DateTimeException("Month out of range");
            return parsed;
        } catch(DateTimeException error) { throw new DiligenceException(HttpStatus.BAD_REQUEST,"INVALID_DILIGENCE_MONTH","Chọn tháng hợp lệ từ năm 1900 đến tháng hiện tại."); }
    }
    private boolean finalized(YearMonth month,Instant now){return month.isBefore(YearMonth.from(now.atZone(ZONE)));}
}
