package com.company.hr.attendance.shiftpass.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.notification.NotificationApi;
import com.company.hr.attendance.shiftpass.api.ShiftPassModels.*;
import com.company.hr.attendance.shiftpass.domain.ShiftPass;
import com.company.hr.attendance.shiftpass.domain.ShiftPassNotification;
import com.company.hr.attendance.shiftpass.domain.ShiftPassStatus;
import com.company.hr.attendance.shiftpass.infrastructure.ShiftPassNotificationRepository;
import com.company.hr.attendance.shiftpass.infrastructure.ShiftPassRepository;
import com.company.hr.attendance.workschedule.WorkScheduleApi;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShiftPassService {
    private final ShiftPassRepository passes;
    private final ShiftPassNotificationRepository notifications;
    private final WorkScheduleApi workSchedule;
    private final IdentityApi identity;
    private final NotificationApi notificationApi;
    private final Clock clock;

    @Autowired
    public ShiftPassService(
        ShiftPassRepository passes,
        ShiftPassNotificationRepository notifications,
        WorkScheduleApi workSchedule,
        IdentityApi identity,
        NotificationApi notificationApi
    ) {
        this(passes, notifications, workSchedule, identity, notificationApi, Clock.systemUTC());
    }

    public ShiftPassService(
        ShiftPassRepository passes,
        ShiftPassNotificationRepository notifications,
        WorkScheduleApi workSchedule,
        IdentityApi identity,
        NotificationApi notificationApi,
        Clock clock
    ) {
        this.passes = passes;
        this.notifications = notifications;
        this.workSchedule = workSchedule;
        this.identity = identity;
        this.notificationApi = notificationApi;
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
