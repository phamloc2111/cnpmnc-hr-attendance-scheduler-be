package com.company.hr.attendance.leave.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.leave.api.LeaveModels.*;
import com.company.hr.attendance.leave.domain.LeaveRequest;
import com.company.hr.attendance.leave.domain.LeaveStatus;
import com.company.hr.attendance.leave.infrastructure.LeaveRequestRepository;
import com.company.hr.attendance.workschedule.domain.WorkSchedule;
import com.company.hr.attendance.workschedule.infrastructure.ShiftTemplateRepository;
import com.company.hr.attendance.workschedule.infrastructure.WorkScheduleRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class LeaveService {
    public static final int MAX_LEAVE_PER_MONTH = 2;

    private final LeaveRequestRepository leaveRepository;
    private final WorkScheduleRepository scheduleRepository;
    private final ShiftTemplateRepository shiftRepository;
    private final IdentityApi identity;
    private final JdbcTemplate jdbc;

    public LeaveService(
        LeaveRequestRepository leaveRepository,
        WorkScheduleRepository scheduleRepository,
        ShiftTemplateRepository shiftRepository,
        IdentityApi identity,
        JdbcTemplate jdbc
    ) {
        this.leaveRepository = leaveRepository;
        this.scheduleRepository = scheduleRepository;
        this.shiftRepository = shiftRepository;
        this.identity = identity;
        this.jdbc = jdbc;
    }

    public LeaveQuotaResponse getQuota(UUID employeeId, YearMonth month) {
        var person = identity.person(employeeId);
        if (person == null || person.locked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không hợp lệ hoặc đã bị khóa.");
        }
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        long approved = leaveRepository.countByEmployeeIdAndStatusAndWorkDateBetween(employeeId, LeaveStatus.APPROVED, start, end);
        long pending = leaveRepository.countByEmployeeIdAndStatusAndWorkDateBetween(employeeId, LeaveStatus.PENDING, start, end);
        int remaining = Math.max(0, MAX_LEAVE_PER_MONTH - (int) approved - (int) pending);

        return new LeaveQuotaResponse(
            month.toString(),
            MAX_LEAVE_PER_MONTH,
            (int) approved,
            (int) pending,
            remaining,
            remaining > 0
        );
    }

    @Transactional
    public LeaveResponse submit(UUID employeeId, CreateLeaveRequest request) {
        var person = identity.lockPerson(employeeId);
        if (person == null || person.locked() || !person.role().equals("EMPLOYEE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ nhân viên mới có thể nộp đơn xin nghỉ phép.");
        }

        YearMonth ym = YearMonth.from(request.workDate());
        var quota = getQuota(employeeId, ym);
        if (!quota.canSubmit()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn đã hết số lần xin nghỉ phép trong tháng " + ym + " (tối đa 2 lần/tháng).");
        }

        UUID scheduleId = request.scheduleId();
        String shiftName = "Ca làm việc";
        UUID shiftTemplateId = request.shiftTemplateId();

        if (scheduleId != null) {
            var schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ca làm việc."));
            if (!schedule.getEmployeeId().equals(employeeId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ca làm việc này không thuộc về bạn.");
            }
            if (leaveRepository.existsByScheduleIdAndStatusIn(scheduleId, List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn đã có đơn xin nghỉ phép đang chờ hoặc đã duyệt cho ca này.");
            }
            shiftName = schedule.getShiftName();
            shiftTemplateId = schedule.getShiftTemplateId();
        } else if (shiftTemplateId != null) {
            var shift = shiftRepository.findById(shiftTemplateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy mẫu ca."));
            shiftName = shift.getName();
        }

        Instant now = Instant.now();
        LeaveRequest leaveRequest = new LeaveRequest(
            UUID.randomUUID(),
            employeeId,
            scheduleId,
            request.workDate(),
            shiftTemplateId,
            shiftName,
            request.reason(),
            now
        );

        leaveRepository.save(leaveRequest);
        return LeaveResponse.from(leaveRequest, person.name(), null);
    }

    public List<LeaveResponse> getMyLeaves(UUID employeeId) {
        var person = identity.person(employeeId);
        if (person == null || person.locked()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản bị khóa.");
        }
        var list = leaveRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
        return list.stream().map(req -> {
            String reviewerName = req.getReviewerId() != null ? getPersonName(req.getReviewerId()) : null;
            return LeaveResponse.from(req, person.name(), reviewerName);
        }).toList();
    }

    public PageResponse<LeaveResponse> listRequests(UUID actorId, LeaveStatus status, LocalDate from, LocalDate to, UUID employeeId, int page, int size) {
        var actor = identity.person(actorId);
        if (actor == null || actor.locked() || (!actor.role().equals("MANAGER") && !actor.role().equals("ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem danh sách đơn nghỉ phép.");
        }

        Specification<LeaveRequest> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("workDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("workDate"), to));
            }
            if (employeeId != null) {
                predicates.add(cb.equal(root.get("employeeId"), employeeId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<LeaveRequest> paged = leaveRepository.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        var mapped = paged.map(req -> {
            String empName = getPersonName(req.getEmployeeId());
            String revName = req.getReviewerId() != null ? getPersonName(req.getReviewerId()) : null;
            return LeaveResponse.from(req, empName, revName);
        });

        return PageResponse.from(mapped);
    }

    @Transactional
    public LeaveResponse review(UUID actorId, UUID requestId, ReviewLeaveRequest reviewRequest) {
        var actor = identity.person(actorId);
        if (actor == null || actor.locked() || (!actor.role().equals("MANAGER") && !actor.role().equals("ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền duyệt đơn nghỉ phép.");
        }

        var req = leaveRepository.findById(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn xin nghỉ phép."));

        if (req.getStatus() != LeaveStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn xin nghỉ phép đã được xử lý trước đó.");
        }

        Instant now = Instant.now();
        String action = reviewRequest.action().toUpperCase();
        if ("APPROVE".equals(action)) {
            req.approve(actorId, reviewRequest.note(), now);
            if (req.getScheduleId() != null) {
                try {
                    jdbc.update("UPDATE work_schedules SET understaffed = TRUE WHERE id = ?", req.getScheduleId());
                } catch (Exception ex) {
                    // ignore if column not yet added
                }
            }
        } else if ("REJECT".equals(action)) {
            req.reject(actorId, reviewRequest.note(), now);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hành động không hợp lệ: phải là APPROVE hoặc REJECT.");
        }

        leaveRepository.save(req);

        String title = "Kết quả đơn xin nghỉ phép";
        String body = "APPROVE".equals(action)
            ? String.format("Đơn xin nghỉ ca '%s' ngày %s của bạn đã được duyệt.", req.getShiftName(), req.getWorkDate())
            : String.format("Đơn xin nghỉ ca '%s' ngày %s của bạn đã bị từ chối.", req.getShiftName(), req.getWorkDate());
        try {
            jdbc.update(
                "INSERT INTO notification_logs (id, employee_id, kind, title, body, reference_id, sent_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), req.getEmployeeId(), "LEAVE_RESULT", title, body, req.getId(), now
            );
        } catch (Exception ex) {
            // non-fatal
        }

        String empName = getPersonName(req.getEmployeeId());
        return LeaveResponse.from(req, empName, actor.name());
    }

    private String getPersonName(UUID id) {
        try {
            var person = identity.person(id);
            return person != null ? person.name() : "Nhân viên";
        } catch (Exception ex) {
            return "Nhân viên";
        }
    }
}
