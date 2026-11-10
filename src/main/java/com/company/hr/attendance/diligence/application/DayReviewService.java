package com.company.hr.attendance.diligence.application;
import com.company.hr.attendance.diligence.domain.DayReview;
import com.company.hr.attendance.diligence.domain.DayReview.*;
import com.company.hr.attendance.diligence.api.DiligenceModels.*;
import com.company.hr.attendance.diligence.infrastructure.DayReviewRepository;
import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly=true)
public class DayReviewService {
    private final IdentityApi identity;private final WorkScheduleApi schedules;private final com.company.hr.attendance.attendance.AttendanceApi attendance;private final DayReviewRepository reviews;private final Clock clock;private final ObjectMapper mapper;
    public DayReviewService(IdentityApi identity,WorkScheduleApi schedules,com.company.hr.attendance.attendance.AttendanceApi attendance,DayReviewRepository reviews,@Qualifier("diligenceClock") Clock clock,ObjectMapper mapper){this.identity=identity;this.schedules=schedules;this.attendance=attendance;this.reviews=reviews;this.clock=clock;this.mapper=mapper;}
    public ReviewHistory history(UUID actor,UUID employee,LocalDate date){
        authorize(actor,employee,false);validateDay(employee,date);var records=reviews.history(employee,date);
        var names=identity.names(records.stream().map(DayReviewRepository.Audit::actorId).toList());
        var audits=records.stream().map(record->{
            try{return new ReviewAudit(record.revision(),record.actorId(),names.getOrDefault(record.actorId(),"Quản lý"),record.at(),mapper.readValue(record.snapshot(),DayReview.class));}
            catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalStateException("Invalid diligence audit snapshot",error);}
        }).toList();
        return new ReviewHistory(reviews.find(employee,date),audits);
    }
    @Transactional
    public DayReview save(UUID actor,UUID employee,LocalDate date,ReviewRequest request){
        authorize(actor,employee,true);var shifts=validateDay(employee,date);
        if(request.note().strip().length()<10)throw invalid("Ghi rõ lý do và căn cứ xác nhận (ít nhất 10 ký tự).");
        if(request.absence()!=Absence.AUTO && request.missing()!=Missing.AUTO)throw invalid("Không đồng thời xác nhận nghỉ và quên chấm trong cùng ngày.");
        var now=clock.instant();
        if(request.missing()!=Missing.AUTO && shifts.stream().anyMatch(shift->now.isBefore(shift.endsAt())))throw invalid("Chỉ xác nhận thiếu lượt chấm sau khi tất cả ca của ngày đã kết thúc.");
        var events=attendance.events(shifts.stream().map(WorkScheduleApi.Shift::id).toList());
        boolean hasCheckIn=events.stream().anyMatch(event->event.type().equals("CHECK_IN"));
        if(request.absence()!=Absence.AUTO && hasCheckIn)throw invalid("Không thể xác nhận nghỉ cả ngày khi đã có lượt chấm vào. Hãy kiểm tra lại ca làm.");
        if(request.missing()!=Missing.AUTO){
            boolean incomplete=shifts.stream().anyMatch(shift->events.stream().noneMatch(event->event.scheduleId().equals(shift.id()) && event.type().equals("CHECK_IN"))
                || events.stream().noneMatch(event->event.scheduleId().equals(shift.id()) && event.type().equals("CHECK_OUT")));
            if(!incomplete)throw invalid("Ngày này đã có đủ lượt chấm vào và ra. Không thể xác nhận quên chấm.");
        }
        var old=reviews.find(employee,date);
        if(old.revision()!=request.revision())throw changed();
        var review=new DayReview(employee,date,request.absence(),request.earlyLeaveApproved(),request.missing(),request.note().strip(),old.revision()+1,actor,now);
        if(!reviews.save(review,old.revision()))throw changed();
        try{reviews.audit(review,mapper.writeValueAsString(review));}catch(com.fasterxml.jackson.core.JsonProcessingException error){throw new IllegalStateException("Could not record diligence audit",error);}
        return review;
    }
    private void authorize(UUID actorId,UUID employeeId,boolean lock){
        var actor=identity.person(actorId);
        if(actor.locked() || !Set.of("MANAGER","ADMIN").contains(actor.role()))throw new AccessDeniedException("Management access required");
        var employee=lock?identity.lockPerson(employeeId):identity.person(employeeId);
        if(!employee.role().equals("EMPLOYEE") || (actor.role().equals("MANAGER") && !actorId.equals(employee.managerId())))throw new AccessDeniedException("Employee unavailable");
    }
    private List<WorkScheduleApi.Shift> validateDay(UUID employee,LocalDate date){
        var today=clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        if(date.getYear()<1900 || date.isAfter(today))throw invalid("Ngày xác minh không được ở tương lai.");
        var shifts=schedules.employeeShifts(List.of(employee),date,date);
        if(shifts.isEmpty())throw new DiligenceException(HttpStatus.NOT_FOUND,"DILIGENCE_DAY_NOT_FOUND","Không có ca làm của nhân viên trong ngày này.");return shifts;
    }
    private DiligenceException invalid(String message){return new DiligenceException(HttpStatus.BAD_REQUEST,"INVALID_DILIGENCE_REVIEW",message);}
    private DiligenceException changed(){return new DiligenceException(HttpStatus.CONFLICT,"DILIGENCE_REVIEW_CHANGED","Xác nhận đã được người khác thay đổi. Hãy tải lại trước khi lưu.");}
}
