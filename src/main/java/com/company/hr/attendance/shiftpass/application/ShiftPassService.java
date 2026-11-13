package com.company.hr.attendance.shiftpass.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.notification.NotificationApi;
import com.company.hr.attendance.shiftpass.api.ShiftPassModels.*;
import com.company.hr.attendance.shiftpass.domain.ShiftPass;
import com.company.hr.attendance.shiftpass.domain.ShiftPassNotification;
import com.company.hr.attendance.shiftpass.domain.ShiftPassStatus;
import com.company.hr.attendance.shiftpass.infrastructure.ShiftPassAuditRecorder;
import com.company.hr.attendance.shiftpass.infrastructure.ShiftPassNotificationRepository;
import com.company.hr.attendance.shiftpass.infrastructure.ShiftPassRepository;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftPassService {
    public static final long MAX_WEEKLY_WORKING_MINUTES = 48L * 60L;

    private final ShiftPassRepository passes;
    private final ShiftPassNotificationRepository notifications;
    private final WorkScheduleApi workSchedule;
    private final IdentityApi identity;
    private final NotificationApi notificationApi;
    private final ShiftPassAuditRecorder auditRecorder;
    private final Clock clock;

    @Autowired
    public ShiftPassService(
        ShiftPassRepository passes,
        ShiftPassNotificationRepository notifications,
        WorkScheduleApi workSchedule,
        IdentityApi identity,
        NotificationApi notificationApi,
        ShiftPassAuditRecorder auditRecorder
    ) {
        this(passes, notifications, workSchedule, identity, notificationApi, auditRecorder, Clock.systemUTC());
    }

    public ShiftPassService(
        ShiftPassRepository passes,
        ShiftPassNotificationRepository notifications,
        WorkScheduleApi workSchedule,
        IdentityApi identity,
        NotificationApi notificationApi,
        Clock clock
    ) {
        this(passes, notifications, workSchedule, identity, notificationApi, null, clock);
    }

    public ShiftPassService(
        ShiftPassRepository passes,
        ShiftPassNotificationRepository notifications,
        WorkScheduleApi workSchedule,
        IdentityApi identity,
        NotificationApi notificationApi,
        ShiftPassAuditRecorder auditRecorder,
        Clock clock
    ) {
        this.passes = passes;
        this.notifications = notifications;
        this.workSchedule = workSchedule;
        this.identity = identity;
        this.notificationApi = notificationApi;
        this.auditRecorder = auditRecorder;
        this.clock = clock;
    }

    @Transactional
    public ShiftPassResponse postPass(UUID actorId, CreateShiftPassRequest request) {
        var actor = requireActiveEmployee(actorId, true);
        var shift = workSchedule.findDetail(request.scheduleId())
            .orElseThrow(() -> new ShiftPassException(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND", "Không tìm thấy ca làm cần pass."));
        if (!shift.employeeId().equals(actorId)) {
            throw new AccessDeniedException("Bạn chỉ có thể đăng pass ca làm được phân cho chính mình.");
        }
        Instant now = clock.instant();
        if (!shift.startsAt().isAfter(now)) {
            throw new ShiftPassException(HttpStatus.BAD_REQUEST, "SHIFT_ALREADY_STARTED",
                "Không thể đăng pass ca đã bắt đầu hoặc đã kết thúc.");
        }
        if (passes.existsByScheduleIdAndStatus(shift.id(), ShiftPassStatus.OPEN)) {
            throw new ShiftPassException(HttpStatus.CONFLICT, "PASS_ALREADY_OPEN",
                "Ca làm này đã có yêu cầu pass ca đang mở.");
        }

        UUID positionId = shift.requiredPositionId();
        String positionName = shift.requiredPosition();
        if (positionId == null) {
            var profile = identity.scoreProfiles(List.of(actorId)).get(actorId);
            if (profile != null) {
                positionId = profile.positionId();
            }
        }

        var pass = new ShiftPass(
            shift.id(),
            actorId,
            shift.workDate(),
            shift.name(),
            shift.startsAt(),
            shift.endsAt(),
            shift.breakMinutes(),
            positionId,
            positionName,
            request.reason(),
            now
        );
        passes.save(pass);

        List<UUID> recipientIds = resolveSamePositionColleagues(actorId, actor.managerId(), positionId);
        String title = "Có ca cần pass: " + shift.name();
        String body = actor.name() + " đăng pass ca " + shift.name() + " ngày " + shift.workDate()
            + (positionName != null && !positionName.isBlank() ? " (Vị trí: " + positionName + ")" : "")
            + ". Bấm để xem chi tiết.";

        if (!recipientIds.isEmpty()) {
            List<ShiftPassNotification> items = recipientIds.stream()
                .map(id -> new ShiftPassNotification(pass.getId(), id, "EMPLOYEE", "PASS_OFFERED", title, body, now))
                .toList();
            notifications.saveAll(items);
            if (notificationApi != null) {
                notificationApi.pushToUsers(
                    recipientIds,
                    title,
                    body,
                    "shift-pass-" + pass.getId(),
                    Map.of(
                        "kind", "SHIFT_PASS_OFFERED",
                        "shiftPassId", pass.getId().toString(),
                        "scheduleId", shift.id().toString()
                    ),
                    shift.startsAt()
                );
            }
        }

        return ShiftPassResponse.from(pass, actor.name(), null, recipientIds.size());
    }

    @Transactional
    public List<ShiftPassResponse> myPasses(UUID actorId) {
        requireActiveEmployee(actorId, false);
        Instant now = clock.instant();
        var list = passes.findByRequesterIdOrClaimerIdOrderByCreatedAtDesc(actorId, actorId);
        expireStalePasses(list, now);
        return mapResponses(list);
    }

    @Transactional
    public List<AvailableShiftPassResponse> availablePasses(UUID actorId) {
        requireActiveEmployee(actorId, false);
        Instant now = clock.instant();
        var openPasses = passes.findByStatusOrderByStartsAtAsc(ShiftPassStatus.OPEN);
        expireStalePasses(openPasses, now);

        var profile = identity.scoreProfiles(List.of(actorId)).get(actorId);
        UUID actorPositionId = profile != null ? profile.positionId() : null;

        List<ShiftPass> candidates = openPasses.stream()
            .filter(p -> p.getStatus() == ShiftPassStatus.OPEN)
            .filter(p -> !p.getRequesterId().equals(actorId))
            .filter(p -> p.getRequiredPositionId() == null || actorPositionId == null || p.getRequiredPositionId().equals(actorPositionId))
            .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        LocalDate minWeekStart = candidates.stream()
            .map(p -> weekStart(p.getWorkDate()))
            .min(LocalDate::compareTo)
            .orElseThrow();
        LocalDate maxWeekEnd = candidates.stream()
            .map(p -> weekStart(p.getWorkDate()).plusDays(6))
            .max(LocalDate::compareTo)
            .orElseThrow();

        List<WorkScheduleApi.Shift> myShifts = workSchedule.employeeShifts(
            List.of(actorId),
            minWeekStart.minusDays(1),
            maxWeekEnd.plusDays(1)
        );

        Set<UUID> requesterIds = candidates.stream().map(ShiftPass::getRequesterId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, String> names = requesterIds.isEmpty() ? Map.of() : identity.names(requesterIds);

        List<AvailableShiftPassResponse> result = new ArrayList<>(candidates.size());
        for (var pass : candidates) {
            long passMinutes = workingMinutes(pass.getStartsAt(), pass.getEndsAt(), pass.getBreakMinutes());
            var evaluation = evaluateEligibility(pass, passMinutes, myShifts);
            result.add(AvailableShiftPassResponse.from(
                pass,
                names.getOrDefault(pass.getRequesterId(), "Nhân viên"),
                passMinutes,
                evaluation.eligible(),
                evaluation.reason(),
                evaluation.currentWeekMinutes(),
                MAX_WEEKLY_WORKING_MINUTES
            ));
        }
        return result;
    }

    @Transactional
    public ShiftPassResponse claimPass(UUID actorId, UUID passId) {
        var claimer = requireActiveEmployee(actorId, true);
        var pass = passes.lockById(passId)
            .orElseThrow(() -> new ShiftPassException(HttpStatus.NOT_FOUND, "PASS_NOT_FOUND", "Không tìm thấy yêu cầu pass ca."));

        Instant now = clock.instant();
        if (pass.getStatus() == ShiftPassStatus.OPEN && !pass.getStartsAt().isAfter(now)) {
            pass.expire(now);
            passes.save(pass);
            notifications.closeOpenByPassId(passId, now);
            throw new ShiftPassException(HttpStatus.CONFLICT, "PASS_EXPIRED", "Ca làm đã bắt đầu nên yêu cầu pass ca đã hết hạn.");
        }
        if (pass.getStatus() != ShiftPassStatus.OPEN) {
            throw new ShiftPassException(HttpStatus.CONFLICT, "PASS_ALREADY_CLOSED",
                "Ca pass này đã có người khác nhận hoặc đã đóng.");
        }
        if (pass.getRequesterId().equals(actorId)) {
            throw new ShiftPassException(HttpStatus.BAD_REQUEST, "CANNOT_CLAIM_OWN_PASS",
                "Bạn không thể tự nhận lại yêu cầu pass ca của chính mình.");
        }

        if (pass.getRequiredPositionId() != null) {
            var profile = identity.scoreProfiles(List.of(actorId)).get(actorId);
            UUID claimerPositionId = profile != null ? profile.positionId() : null;
            if (claimerPositionId != null && !pass.getRequiredPositionId().equals(claimerPositionId)) {
                throw new ShiftPassException(HttpStatus.FORBIDDEN, "POSITION_MISMATCH",
                    "Ca này yêu cầu đúng vị trí công việc tương ứng.");
            }
        }

        LocalDate ws = weekStart(pass.getWorkDate());
        LocalDate we = ws.plusDays(6);
        List<WorkScheduleApi.Shift> claimerShifts = workSchedule.employeeShifts(
            List.of(actorId),
            ws.minusDays(1),
            we.plusDays(1)
        );
        long passMinutes = workingMinutes(pass.getStartsAt(), pass.getEndsAt(), pass.getBreakMinutes());
        var eligibility = evaluateEligibility(pass, passMinutes, claimerShifts);
        if (!eligibility.eligible()) {
            throw new ShiftPassException(HttpStatus.CONFLICT, eligibility.errorCode(), eligibility.reason());
        }

        boolean transferred = workSchedule.transferShift(pass.getScheduleId(), pass.getRequesterId(), actorId);
        if (!transferred) {
            throw new ShiftPassException(HttpStatus.CONFLICT, "SCHEDULE_CONFLICT",
                "Không thể chuyển ca do lịch làm việc vừa thay đổi hoặc bị trùng giờ.");
        }

        pass.markClaimed(actorId, now);
        passes.save(pass);
        notifications.closeOpenByPassId(passId, now);

        var requester = identity.person(pass.getRequesterId());
        List<ShiftPassNotification> followUpNotifs = new ArrayList<>();
        String ownerTitle = "Ca pass đã được nhận: " + pass.getShiftName();
        String ownerBody = claimer.name() + " đã nhận ca " + pass.getShiftName() + " ngày " + pass.getWorkDate() + " thay cho bạn.";
        followUpNotifs.add(new ShiftPassNotification(passId, requester.id(), "EMPLOYEE", "PASS_CLAIMED_OWNER", ownerTitle, ownerBody, now));

        UUID managerId = requester.managerId() != null ? requester.managerId() : claimer.managerId();
        if (managerId != null) {
            String mgrTitle = "Biến động pass ca: " + pass.getShiftName();
            String mgrBody = claimer.name() + " đã nhận pass ca " + pass.getShiftName()
                + " ngày " + pass.getWorkDate() + " từ " + requester.name() + ".";
            followUpNotifs.add(new ShiftPassNotification(passId, managerId, "MANAGER", "PASS_CLAIMED_MANAGER", mgrTitle, mgrBody, now));
        }
        notifications.saveAll(followUpNotifs);

        if (notificationApi != null) {
            notificationApi.pushToUsers(
                List.of(requester.id()),
                ownerTitle,
                ownerBody,
                "shift-pass-owner-" + passId,
                Map.of("kind", "SHIFT_PASS_CLAIMED", "shiftPassId", passId.toString()),
                pass.getStartsAt()
            );
            if (managerId != null) {
                notificationApi.pushToUsers(
                    List.of(managerId),
                    "Biến động pass ca: " + pass.getShiftName(),
                    claimer.name() + " đã nhận pass ca " + pass.getShiftName() + " ngày " + pass.getWorkDate() + " từ " + requester.name() + ".",
                    "shift-pass-manager-" + passId,
                    Map.of("kind", "SHIFT_PASS_MANAGER_ALERT", "shiftPassId", passId.toString()),
                    pass.getStartsAt()
                );
            }
        }

        if (auditRecorder != null) {
            auditRecorder.recordClaim(
                actorId,
                requester.id(),
                pass.getWorkDate(),
                "Nhận pass ca " + pass.getShiftName() + " ngày " + pass.getWorkDate() + " từ " + requester.name() + " sang " + claimer.name(),
                "Nhân viên phụ trách: " + requester.name() + " (" + requester.id() + "), Ca: " + pass.getShiftName(),
                "Nhân viên phụ trách: " + claimer.name() + " (" + claimer.id() + "), Ca: " + pass.getShiftName(),
                now
            );
        }

        return ShiftPassResponse.from(pass, requester.name(), claimer.name(), 0);
    }

    @Transactional
    public void cancelPass(UUID actorId, UUID passId) {
        requireActiveEmployee(actorId, false);
        var pass = passes.lockById(passId)
            .orElseThrow(() -> new ShiftPassException(HttpStatus.NOT_FOUND, "PASS_NOT_FOUND", "Không tìm thấy yêu cầu pass ca."));
        if (!pass.getRequesterId().equals(actorId)) {
            throw new AccessDeniedException("Bạn chỉ có thể hủy yêu cầu pass ca do chính mình tạo.");
        }
        if (pass.getStatus() != ShiftPassStatus.OPEN) {
            throw new ShiftPassException(HttpStatus.CONFLICT, "PASS_NOT_OPEN", "Yêu cầu pass ca này không còn ở trạng thái mở.");
        }
        Instant now = clock.instant();
        pass.cancel(now);
        passes.save(pass);
        notifications.closeOpenByPassId(passId, now);
    }

    @Transactional
    public List<ShiftPassNotificationResponse> myNotifications(UUID actorId) {
        var actor = identity.person(actorId);
        if (actor.locked()) {
            throw new AccessDeniedException("Tài khoản đã bị khóa.");
        }
        Instant now = clock.instant();
        var items = notifications.findByRecipientIdOrderByCreatedAtDesc(actorId);
        var passIds = items.stream().map(ShiftPassNotification::getShiftPassId).collect(java.util.stream.Collectors.toSet());
        if (!passIds.isEmpty()) {
            Map<UUID, ShiftPass> passMap = new HashMap<>();
            passes.findAllById(passIds).forEach(p -> passMap.put(p.getId(), p));
            for (var item : items) {
                if ("OPEN".equals(item.getStatus()) && "PASS_OFFERED".equals(item.getKind())) {
                    var pass = passMap.get(item.getShiftPassId());
                    if (pass == null || pass.getStatus() != ShiftPassStatus.OPEN || !pass.getStartsAt().isAfter(now)) {
                        if (pass != null && pass.getStatus() == ShiftPassStatus.OPEN && !pass.getStartsAt().isAfter(now)) {
                            pass.expire(now);
                            passes.save(pass);
                        }
                        item.close(now);
                        notifications.save(item);
                    }
                }
            }
        }
        return items.stream().map(ShiftPassNotificationResponse::from).toList();
    }

    private record Eligibility(boolean eligible, String errorCode, String reason, long currentWeekMinutes) {}

    private Eligibility evaluateEligibility(ShiftPass pass, long passMinutes, List<WorkScheduleApi.Shift> shifts) {
        LocalDate ws = weekStart(pass.getWorkDate());
        LocalDate we = ws.plusDays(6);
        boolean overlaps = false;
        long currentWeekMinutes = 0L;

        for (var shift : shifts) {
            if (!shift.workDate().isBefore(ws) && !shift.workDate().isAfter(we)) {
                currentWeekMinutes += workingMinutes(shift.startsAt(), shift.endsAt(), shift.breakMinutes());
            }
            if (shift.startsAt().isBefore(pass.getEndsAt()) && shift.endsAt().isAfter(pass.getStartsAt())) {
                overlaps = true;
            }
        }

        if (overlaps) {
            return new Eligibility(false, "SCHEDULE_OVERLAP", "Trùng giờ với ca làm bạn đã được phân.", currentWeekMinutes);
        }
        if (currentWeekMinutes + passMinutes > MAX_WEEKLY_WORKING_MINUTES) {
            return new Eligibility(
                false,
                "WEEKLY_HOUR_LIMIT_EXCEEDED",
                "Nhận thêm ca này sẽ vượt quá giới hạn tối đa 48 giờ/tuần.",
                currentWeekMinutes
            );
        }
        return new Eligibility(true, null, null, currentWeekMinutes);
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static long workingMinutes(Instant startsAt, Instant endsAt, int breakMinutes) {
        return Math.max(0L, Duration.between(startsAt, endsAt).toMinutes() - breakMinutes);
    }

    private List<UUID> resolveSamePositionColleagues(UUID requesterId, UUID managerId, UUID positionId) {
        List<UUID> candidates;
        if (positionId != null) {
            candidates = identity.activeEmployeeIdsByPosition(positionId);
        } else if (managerId != null) {
            candidates = identity.activeEmployeeIdsByManager(managerId);
        } else {
            candidates = List.of();
        }
        return candidates.stream()
            .filter(id -> !id.equals(requesterId))
            .distinct()
            .toList();
    }

    private IdentityApi.Person requireActiveEmployee(UUID actorId, boolean lock) {
        var actor = lock ? identity.lockPerson(actorId) : identity.person(actorId);
        if (actor.locked() || !"EMPLOYEE".equals(actor.role())) {
            throw new AccessDeniedException("Chỉ nhân viên đang hoạt động mới có quyền thực hiện thao tác này.");
        }
        return actor;
    }

    private void expireStalePasses(List<ShiftPass> list, Instant now) {
        for (var pass : list) {
            if (pass.getStatus() == ShiftPassStatus.OPEN && !pass.getStartsAt().isAfter(now)) {
                pass.expire(now);
                passes.save(pass);
                notifications.closeOpenByPassId(pass.getId(), now);
            }
        }
    }

    private List<ShiftPassResponse> mapResponses(List<ShiftPass> list) {
        Set<UUID> userIds = new HashSet<>();
        for (var pass : list) {
            userIds.add(pass.getRequesterId());
            if (pass.getClaimerId() != null) userIds.add(pass.getClaimerId());
        }
        Map<UUID, String> names = userIds.isEmpty() ? Map.of() : identity.names(userIds);
        return list.stream()
            .map(pass -> ShiftPassResponse.from(
                pass,
                names.getOrDefault(pass.getRequesterId(), "Nhân viên"),
                pass.getClaimerId() == null ? null : names.getOrDefault(pass.getClaimerId(), "Nhân viên"),
                0
            ))
            .toList();
    }
}
