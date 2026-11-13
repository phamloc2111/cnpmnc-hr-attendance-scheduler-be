package com.company.hr.attendance.attendance.application;

import com.company.hr.attendance.attendance.api.AttendanceExplanationModels.*;
import com.company.hr.attendance.attendance.api.AttendanceModels.EventResponse;
import com.company.hr.attendance.attendance.api.AttendanceModels.EventType;
import com.company.hr.attendance.attendance.domain.AttendanceExplanation;
import com.company.hr.attendance.attendance.domain.AttendanceExplanation.*;
import com.company.hr.attendance.attendance.domain.AttendanceStatus;
import com.company.hr.attendance.attendance.infrastructure.AttendanceEventRepository;
import com.company.hr.attendance.attendance.infrastructure.AttendanceExplanationRepository;
import com.company.hr.attendance.attendance.infrastructure.AttendanceExplanationImageRepository;
import com.company.hr.attendance.attendance.infrastructure.AttendanceRecordRepository;
import com.company.hr.attendance.audit.AuditApi;
import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import java.io.IOException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly=true)
public class AttendanceExplanationService {
    private final IdentityApi identity;
    private final WorkScheduleApi schedules;
    private final AttendanceEventRepository events;
    private final AttendanceRecordRepository records;
    private final AttendanceExplanationRepository explanations;
    private final AttendanceExplanationImageRepository images;
    private final Clock clock;
    private final int graceMinutes;
    private final AuditApi audit;

    public AttendanceExplanationService(IdentityApi identity,WorkScheduleApi schedules,AttendanceEventRepository events,
        AttendanceRecordRepository records,AttendanceExplanationRepository explanations,AttendanceExplanationImageRepository images,
        Clock clock, int graceMinutes){
        this(identity,schedules,events,records,explanations,images,clock,graceMinutes,null);
    }

    @Autowired
    public AttendanceExplanationService(IdentityApi identity,WorkScheduleApi schedules,AttendanceEventRepository events,
        AttendanceRecordRepository records,AttendanceExplanationRepository explanations,AttendanceExplanationImageRepository images,
        @org.springframework.beans.factory.annotation.Qualifier("diligenceClock") Clock clock,
        @Value("${app.attendance.grace-minutes:5}") int graceMinutes,
        AuditApi audit){
        this.identity=identity;this.schedules=schedules;this.events=events;this.records=records;
        this.explanations=explanations;this.images=images;this.clock=clock;this.graceMinutes=graceMinutes;
        this.audit=audit;
    }

    private static final int MAX_IMAGE_BYTES=5*1024*1024;
    private record PreparedImage(String contentType,byte[] bytes) {}
    @Transactional
    public ExplanationResponse submit(UUID actorId,SubmitRequest request){return submit(actorId,request,List.of());}
    @Transactional
    public ExplanationResponse submit(UUID actorId,SubmitRequest request,List<MultipartFile> files){
        var prepared=prepareImages(files);
        var actor=identity.person(actorId);
        if(actor.locked()||!actor.role().equals("EMPLOYEE"))throw new org.springframework.security.access.AccessDeniedException("Employee access required");
        var shift=schedules.lock(request.scheduleId()).orElseThrow(()->missing("SHIFT_NOT_FOUND","Không tìm thấy ca làm."));
        if(!shift.employeeId().equals(actorId))throw missing("SHIFT_NOT_FOUND","Không tìm thấy ca làm.");
        var evidence=events.scheduleEvents(List.of(shift.id()));
        if(!eligible(shift,request.category(),request.attendanceRecordId(),evidence,clock.instant()))
            throw invalid("EXPLANATION_NOT_ELIGIBLE","Ca làm không có lỗi chấm công tương ứng để giải trình.");
        if(request.reason()==null||request.reason().strip().length()<10||request.reason().strip().length()>2000)throw invalid("EXPLANATION_REASON_REQUIRED","Lý do giải trình cần từ 10 đến 2000 ký tự.");
        if(explanations.forSchedules(List.of(shift.id())).stream().anyMatch(existing->existing.category()==request.category()
            && Objects.equals(existing.attendanceRecordId(),request.attendanceRecordId())))
            throw new AttendanceException(HttpStatus.CONFLICT,"EXPLANATION_ALREADY_EXISTS","Giải trình cho lượt chấm này đã được gửi.");
        var row=new AttendanceExplanation(UUID.randomUUID(),shift.id(),actorId,shift.workDate(),request.category(),request.attendanceRecordId(),
            request.reason().strip(),clock.instant(),Status.PENDING,null,false,null,null);
        if(!explanations.insert(row))throw new AttendanceException(HttpStatus.CONFLICT,"EXPLANATION_ALREADY_EXISTS","Giải trình cho lượt chấm này đã được gửi.");
        for(int index=0;index<prepared.size();index++){var image=prepared.get(index);images.insert(row.id(),index,image.contentType(),image.bytes());}
        return response(row,actor.name());
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
                    candidates.add(new Candidate(shift.id(),shift.workDate(),shift.name(),shift.startsAt(),shift.endsAt(),category,null,row==null?null:response(row,actor.name())));
                }
            }
            for(var record:rejected.getOrDefault(shift.id(),List.of())){
                var row=existingByTarget.get(key(shift.id(),Category.INVALID,record.getId()));
                candidates.add(new Candidate(shift.id(),shift.workDate(),shift.name(),shift.startsAt(),shift.endsAt(),Category.INVALID,record.getId(),row==null?null:response(row,actor.name())));
            }
        }
        candidates.sort(Comparator.comparing(Candidate::workDate).reversed().thenComparing(Candidate::startsAt,Comparator.reverseOrder()).thenComparing(candidate->candidate.category().name()));
        long offset=(long)page*size;var items=candidates.stream().skip(offset).limit(size).toList();
        return new Page<>(items,page,size,candidates.size(),(candidates.size()+size-1)/size);
    }
    public Page<ExplanationResponse> mine(UUID actorId,LocalDate from,LocalDate to,int page,int size){
        var actor=identity.person(actorId);if(actor.locked()||!actor.role().equals("EMPLOYEE"))throw new org.springframework.security.access.AccessDeniedException("Employee access required");
        validateRange(from,to);var rows=explanations.forEmployee(actorId,from,to);long offset=(long)page*size;
        return new Page<>(rows.stream().skip(offset).limit(size).map(row->response(row,actor.name())).toList(),page,size,rows.size(),(rows.size()+size-1)/size);
    }
    public Page<ExplanationResponse> managed(UUID actorId,LocalDate from,LocalDate to,Status status,int page,int size){
        var actor=identity.person(actorId);if(actor.locked()||!Set.of("MANAGER","ADMIN").contains(actor.role()))throw new org.springframework.security.access.AccessDeniedException("Management access required");
        validateRange(from,to);var employees=actor.role().equals("MANAGER")?identity.managedEmployeeIds(actorId):
            null;
        long count=explanations.countForEmployees(employees,from,to,status);
        var rows=explanations.forEmployees(employees,from,to,status,size,Math.toIntExact((long)page*size));var names=identity.names(rows.stream().map(AttendanceExplanation::employeeId).toList());
        return new Page<>(rows.stream().map(row->response(row,names.getOrDefault(row.employeeId(),"Nhân viên"))).toList(),page,size,count,(int)((count+size-1)/size));
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
        if(audit != null){
            audit.record(
                actorId,
                employee.id(),
                row.workDate(),
                "ATTENDANCE",
                "EXPLANATION_DECIDED",
                (request.approved() ? "Duyệt" : "Từ chối") + " giải trình " + row.category() + " của " + employee.name() + " ngày " + row.workDate(),
                "Trạng thái: PENDING, Loại: " + row.category(),
                String.format("Trạng thái: %s, Miễn phạt trễ: %s, Lý do: %s", status, request.exemptLatePenalty(), reason.isBlank() ? "Không có" : reason),
                now
            );
        }
        return response(explanations.find(id).orElseThrow(),employee.name());
    }
    public AttendanceExplanationImageRepository.Photo photo(UUID actorId,UUID explanationId,UUID imageId){
        var actor=identity.person(actorId);
        var row=explanations.find(explanationId).orElseThrow(()->missing("EXPLANATION_NOT_FOUND","Không tìm thấy giải trình."));
        if(actor.locked()||(!actorId.equals(row.employeeId())&&!actor.role().equals("ADMIN")
            && !(actor.role().equals("MANAGER")&&actorId.equals(identity.person(row.employeeId()).managerId()))))
            throw missing("EXPLANATION_NOT_FOUND","Không tìm thấy giải trình.");
        return images.photo(explanationId,imageId).orElseThrow(()->missing("EXPLANATION_IMAGE_NOT_FOUND","Không tìm thấy ảnh minh chứng."));
    }
    private ExplanationResponse response(AttendanceExplanation row,String name){
        var files=images.forExplanation(row.id()).stream().map(image->new ImageInfo(image.id(),
            "/api/v1/attendance/explanations/"+row.id()+"/images/"+image.id(),image.contentType(),image.sizeBytes())).toList();
        return ExplanationResponse.from(row,name,files);
    }
    private List<PreparedImage> prepareImages(List<MultipartFile> files){
        if(files==null||files.isEmpty())return List.of();
        if(files.size()>3)throw invalid("TOO_MANY_EXPLANATION_IMAGES","Chỉ được gửi tối đa 3 ảnh minh chứng.");
        var prepared=new ArrayList<PreparedImage>();
        for(var file:files){
            if(file==null||file.isEmpty()||file.getSize()>MAX_IMAGE_BYTES)
                throw invalid("INVALID_EXPLANATION_IMAGE","Mỗi ảnh phải có dung lượng từ 1 byte đến 5 MB.");
            try{
                var bytes=file.getBytes();
                if(bytes.length==0||bytes.length>MAX_IMAGE_BYTES)throw invalid("INVALID_EXPLANATION_IMAGE","Mỗi ảnh phải có dung lượng từ 1 byte đến 5 MB.");
                var type=imageType(bytes);
                if(type==null)throw invalid("INVALID_EXPLANATION_IMAGE","Chỉ chấp nhận ảnh JPEG, PNG hoặc WebP.");
                prepared.add(new PreparedImage(type,bytes));
            }catch(IOException error){throw invalid("INVALID_EXPLANATION_IMAGE","Không thể đọc ảnh minh chứng. Vui lòng thử lại.");}
        }
        return prepared;
    }
    private String imageType(byte[] bytes){
        if(bytes.length>=3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255)return "image/jpeg";
        if(bytes.length>=8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71&&bytes[4]==13&&bytes[5]==10&&bytes[6]==26&&bytes[7]==10)return "image/png";
        if(bytes.length>=12&&bytes[0]=='R'&&bytes[1]=='I'&&bytes[2]=='F'&&bytes[3]=='F'&&bytes[8]=='W'&&bytes[9]=='E'&&bytes[10]=='B'&&bytes[11]=='P')return "image/webp";
        return null;
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
