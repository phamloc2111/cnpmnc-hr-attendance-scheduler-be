package com.company.hr.attendance.attendance.application;

import com.company.hr.attendance.attendance.api.AttendanceExplanationModels.*;
import com.company.hr.attendance.attendance.api.AttendanceModels.EventResponse;
import com.company.hr.attendance.attendance.api.AttendanceModels.EventType;
import com.company.hr.attendance.attendance.domain.AttendanceExplanation;
import com.company.hr.attendance.attendance.domain.AttendanceExplanation.*;
import com.company.hr.attendance.attendance.domain.AttendanceStatus;
import com.company.hr.attendance.attendance.infrastructure.AttendanceEventRepository;
import com.company.hr.attendance.attendance.infrastructure.AttendanceExplanationRepository;
import com.company.hr.attendance.attendance.infrastructure.AttendanceRecordRepository;
import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class AttendanceExplanationService {
    private final IdentityApi identity;
    private final WorkScheduleApi schedules;
    private final AttendanceEventRepository events;
    private final AttendanceRecordRepository records;
    private final AttendanceExplanationRepository explanations;
    private final Clock clock;
    private final int graceMinutes;
    public AttendanceExplanationService(IdentityApi identity,WorkScheduleApi schedules,AttendanceEventRepository events,
        AttendanceRecordRepository records,AttendanceExplanationRepository explanations,
        @org.springframework.beans.factory.annotation.Qualifier("diligenceClock") Clock clock,
        @Value("${app.attendance.grace-minutes:5}") int graceMinutes){
        this.identity=identity;this.schedules=schedules;this.events=events;this.records=records;
        this.explanations=explanations;this.clock=clock;this.graceMinutes=graceMinutes;
    }
    @Transactional
    public ExplanationResponse submit(UUID actorId,SubmitRequest request){
        var actor=identity.person(actorId);
        if(actor.locked()||!actor.role().equals("EMPLOYEE"))throw new org.springframework.security.access.AccessDeniedException("Employee access required");
        var shift=schedules.lock(request.scheduleId()).orElseThrow(()->missing("SHIFT_NOT_FOUND","Không tìm thấy ca làm."));
        if(!shift.employeeId().equals(actorId))throw missing("SHIFT_NOT_FOUND","Không tìm thấy ca làm.");
        var evidence=events.scheduleEvents(List.of(shift.id()));
        if(!eligible(shift,request.category(),request.attendanceRecordId(),evidence,clock.instant()))
            throw invalid("EXPLANATION_NOT_ELIGIBLE","Ca làm không có lỗi chấm công tương ứng để giải trình.");
        if(request.reason().strip().length()<10)throw invalid("EXPLANATION_REASON_REQUIRED","Lý do giải trình cần ít nhất 10 ký tự.");
        if(explanations.forSchedules(List.of(shift.id())).stream().anyMatch(existing->existing.category()==request.category()
            && Objects.equals(existing.attendanceRecordId(),request.attendanceRecordId())))
            throw new AttendanceException(HttpStatus.CONFLICT,"EXPLANATION_ALREADY_EXISTS","Giải trình cho lượt chấm này đã được gửi.");
        var row=new AttendanceExplanation(UUID.randomUUID(),shift.id(),actorId,shift.workDate(),request.category(),request.attendanceRecordId(),
            request.reason().strip(),clock.instant(),Status.PENDING,null,false,null,null);
        if(!explanations.insert(row))throw new AttendanceException(HttpStatus.CONFLICT,"EXPLANATION_ALREADY_EXISTS","Giải trình cho lượt chấm này đã được gửi.");
        return ExplanationResponse.from(row,actor.name());
    }
    public Page<Candidate> candidates(UUID actorId,LocalDate from,LocalDate to,int page,int size){
        var actor=identity.person(actorId);if(actor.locked()||!actor.role().equals("EMPLOYEE"))throw new org.springframework.security.access.AccessDeniedException("Employee access required");
        validateRange(from,to);var shifts=schedules.employeeShifts(List.of(actorId),from,to);
        var evidence=events.scheduleEvents(shifts.stream().map(WorkScheduleApi.Shift::id).toList()).stream().collect(Collectors.groupingBy(EventResponse::scheduleId));
        var existing=explanations.forSchedules(shifts.stream().map(WorkScheduleApi.Shift::id).toList());
        var existingByTarget=existing.stream().collect(Collectors.toMap(row->key(row.scheduleId(),row.category(),row.attendanceRecordId()),row->row,(a,b)->a));
        var rejected=records.findAllById(explanations.rejectedRecords(actorId,from,to)).stream().filter(record->record.getScheduleId()!=null).collect(Collectors.groupingBy(record->record.getScheduleId()));
        var candidates=new ArrayList<Candidate>();var now=clock.instant();
        for(var shift:shifts){
            var found=evidence.getOrDefault(shift.id(),List.of());
            for(var category:List.of(Category.LATE,Category.MISSING_CHECK_IN,Category.MISSING_CHECK_OUT)){
                if(eligible(shift,category,null,found,now)){
                    var row=existingByTarget.get(key(shift.id(),category,null));
                    candidates.add(new Candidate(shift.id(),shift.workDate(),shift.name(),shift.startsAt(),shift.endsAt(),category,null,row==null?null:ExplanationResponse.from(row,actor.name())));
                }
            }
            for(var record:rejected.getOrDefault(shift.id(),List.of())){
                var row=existingByTarget.get(key(shift.id(),Category.INVALID,record.getId()));
                candidates.add(new Candidate(shift.id(),shift.workDate(),shift.name(),shift.startsAt(),shift.endsAt(),Category.INVALID,record.getId(),row==null?null:ExplanationResponse.from(row,actor.name())));
            }
        }
        candidates.sort(Comparator.comparing(Candidate::workDate).reversed().thenComparing(Candidate::startsAt,Comparator.reverseOrder()).thenComparing(candidate->candidate.category().name()));
        long offset=(long)page*size;var items=candidates.stream().skip(offset).limit(size).toList();
        return new Page<>(items,page,size,candidates.size(),(candidates.size()+size-1)/size);
    }
    public Page<ExplanationResponse> mine(UUID actorId,LocalDate from,LocalDate to,int page,int size){
        var actor=identity.person(actorId);if(actor.locked()||!actor.role().equals("EMPLOYEE"))throw new org.springframework.security.access.AccessDeniedException("Employee access required");
        validateRange(from,to);var rows=explanations.forEmployee(actorId,from,to);long offset=(long)page*size;
        return new Page<>(rows.stream().skip(offset).limit(size).map(row->ExplanationResponse.from(row,actor.name())).toList(),page,size,rows.size(),(rows.size()+size-1)/size);
    }
    public Page<ExplanationResponse> managed(UUID actorId,LocalDate from,LocalDate to,Status status,int page,int size){
        var actor=identity.person(actorId);if(actor.locked()||!Set.of("MANAGER","ADMIN").contains(actor.role()))throw new org.springframework.security.access.AccessDeniedException("Management access required");
        validateRange(from,to);var employees=actor.role().equals("MANAGER")?identity.managedEmployeeIds(actorId):
            null;
        long count=explanations.countForEmployees(employees,from,to,status);
        var rows=explanations.forEmployees(employees,from,to,status,size,Math.toIntExact((long)page*size));var names=identity.names(rows.stream().map(AttendanceExplanation::employeeId).toList());
        return new Page<>(rows.stream().map(row->ExplanationResponse.from(row,names.getOrDefault(row.employeeId(),"Nhân viên"))).toList(),page,size,count,(int)((count+size-1)/size));
    }
    @Transactional
    public ExplanationResponse decide(UUID actorId,UUID id,DecisionRequest request){
        var actor=identity.person(actorId);if(actor.locked()||!Set.of("MANAGER","ADMIN").contains(actor.role()))throw new org.springframework.security.access.AccessDeniedException("Management access required");
        var row=explanations.find(id).orElseThrow(()->missing("EXPLANATION_NOT_FOUND","Không tìm thấy giải trình."));
        var employee=identity.lockPerson(row.employeeId());
        if(!employee.role().equals("EMPLOYEE")||(actor.role().equals("MANAGER")&&!actorId.equals(employee.managerId())))throw missing("EXPLANATION_NOT_FOUND","Không tìm thấy giải trình.");
        if(row.status()!=Status.PENDING)throw new AttendanceException(HttpStatus.CONFLICT,"EXPLANATION_ALREADY_REVIEWED","Giải trình đã được xử lý. Hãy tải lại danh sách.");
        var reason=request.reason()==null?"":request.reason().strip();
        if(!request.approved()&&reason.length()<10)throw invalid("DECISION_REASON_REQUIRED","Từ chối phải ghi lý do ít nhất 10 ký tự.");
        if(request.exemptLatePenalty()&&(!request.approved()||row.category()!=Category.LATE||reason.length()<10))
            throw invalid("INVALID_PENALTY_EXEMPTION","Chỉ miễn điểm trễ khi duyệt giải trình đi trễ và ghi căn cứ ít nhất 10 ký tự.");
        var status=request.approved()?Status.APPROVED:Status.REJECTED;var now=clock.instant();
        if(!explanations.decide(id,actorId,now,status,reason.isBlank()?null:reason,request.exemptLatePenalty()))
            throw new AttendanceException(HttpStatus.CONFLICT,"EXPLANATION_ALREADY_REVIEWED","Giải trình đã được xử lý. Hãy tải lại danh sách.");
        return ExplanationResponse.from(explanations.find(id).orElseThrow(),employee.name());
    }
    private boolean eligible(WorkScheduleApi.Shift shift,Category category,UUID recordId,List<EventResponse> found,Instant now){
        var checkIn=found.stream().filter(event->event.type()==EventType.CHECK_IN).map(EventResponse::receivedAt).min(Comparator.naturalOrder()).orElse(null);
        var checkOut=found.stream().filter(event->event.type()==EventType.CHECK_OUT).map(EventResponse::receivedAt).min(Comparator.naturalOrder()).orElse(null);
        return switch(category){
            case LATE -> recordId==null && checkIn!=null && checkIn.isAfter(shift.startsAt().plus(Duration.ofMinutes(graceMinutes)));
            case MISSING_CHECK_IN -> recordId==null && !now.isBefore(shift.endsAt()) && checkIn==null;
            case MISSING_CHECK_OUT -> recordId==null && !now.isBefore(shift.endsAt()) && checkIn!=null && checkOut==null;
            case INVALID -> recordId!=null && records.findById(recordId).map(record->record.getEmployeeId().equals(shift.employeeId())
                && shift.id().equals(record.getScheduleId()) && record.getStatus()==AttendanceStatus.REJECTED).orElse(false);
        };
    }
    private void validateRange(LocalDate from,LocalDate to){
        if(from==null||to==null||to.isBefore(from)||ChronoUnit.DAYS.between(from,to)>31||to.isAfter(clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()))
            throw invalid("INVALID_EXPLANATION_RANGE","Khoảng ngày phải hợp lệ, tối đa 32 ngày và không ở tương lai.");
    }
    private String key(UUID shift,Category kind,UUID record){return shift+":"+kind+":"+record;}
    private AttendanceException invalid(String code,String message){return new AttendanceException(HttpStatus.BAD_REQUEST,code,message);}
    private AttendanceException missing(String code,String message){return new AttendanceException(HttpStatus.NOT_FOUND,code,message);}
}
