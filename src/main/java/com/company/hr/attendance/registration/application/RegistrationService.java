package com.company.hr.attendance.registration.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.registration.api.RegistrationModels.*;
import com.company.hr.attendance.registration.domain.RegistrationWindow;
import com.company.hr.attendance.registration.infrastructure.RegistrationRepository;
import com.company.hr.attendance.workschedule.ShiftCatalogApi;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(readOnly = true)
public class RegistrationService {
    private final IdentityApi identity;
    private final ShiftCatalogApi catalog;
    private final RegistrationRepository registrations;
    private final Clock clock;
    private final RegistrationAccessService access;
    public RegistrationService(IdentityApi identity, ShiftCatalogApi catalog, RegistrationRepository registrations,
                               @Qualifier("registrationClock") Clock clock, RegistrationAccessService access) {
        this.identity = identity; this.catalog = catalog; this.registrations = registrations; this.clock = clock; this.access = access;
    }
    public Week get(UUID actorId, LocalDate week) {
        authorize(identity.person(actorId)); var now = clock.instant();
        return view(actorId, week == null ? RegistrationWindow.nextWeek(now) : monday(week), now);
    }
    public Catalog catalog(UUID actorId, int page, int size) {
        authorize(identity.person(actorId));
        var result = catalog.page(page, size);
        return new Catalog(result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }
    @Transactional
    public Week save(UUID actorId, Save request) {
        // Serializes replace operations even when this employee has no registration rows yet.
        var person = identity.lockPerson(actorId); authorize(person);
        var week = monday(request.weekStart()); requireOpen(person, week);
        var current = registrations.ids(actorId, week);
        if (!revision(current).equals(request.revision())) throw new RegistrationException(CONFLICT, "STALE_REGISTRATION",
            "Đăng ký đã được thay đổi ở nơi khác. Hãy tải lại trước khi lưu.");
        var ids = new LinkedHashSet<>(request.shiftIds());
        if (ids.size() != request.shiftIds().size()) throw new RegistrationException(BAD_REQUEST, "DUPLICATE_SHIFT", "Mỗi ca chỉ được chọn một lần.");
        if (catalog.find(ids).size() != ids.size()) throw new RegistrationException(BAD_REQUEST, "INVALID_REGISTRATION_SHIFT",
            "Có ca không còn trong danh sách. Hãy tải lại và chọn ca hợp lệ.");
        requireOpen(person, week);
        registrations.replace(actorId, week, ids);
        return view(actorId, week, clock.instant());
    }
    private Week view(UUID employeeId, LocalDate week, Instant now) {
        var ids = registrations.ids(employeeId, week); var window = RegistrationWindow.forWeek(week);
        var selected = catalog.find(ids).stream().sorted(Comparator.comparing(ShiftCatalogApi.Template::name).thenComparing(ShiftCatalogApi.Template::id)).toList();
        boolean reopened = !now.isBefore(window.closesAt()) && access.reopened(identity.person(employeeId), week);
        return new Week(week, week.plusDays(6), now, window.opensAt(), window.closesAt(), window.editable(now) || reopened, reopened, revision(ids), selected);
    }
    private void requireOpen(IdentityApi.Person person, LocalDate week) {
        var now = clock.instant(); var window = RegistrationWindow.forWeek(week);
        if (!window.editable(now) && (now.isBefore(window.closesAt()) || !access.reopened(person, week))) throw new RegistrationException(CONFLICT, "REGISTRATION_CLOSED",
            "Cổng đăng ký đã đóng hoặc chưa mở. Chỉ đăng ký trong ngày Chủ nhật hoặc khi quản lý mở lại riêng cho bạn (giờ Việt Nam).");
    }
    private LocalDate monday(LocalDate week) {
        if (week == null || week.getDayOfWeek() != DayOfWeek.MONDAY) throw new RegistrationException(BAD_REQUEST, "INVALID_REGISTRATION_WEEK",
            "Tuần đăng ký phải bắt đầu vào thứ Hai.");
        return week;
    }
    private void authorize(IdentityApi.Person actor) {
        if (actor.locked() || !actor.role().equals("EMPLOYEE")) throw new RegistrationException(FORBIDDEN, "FORBIDDEN", "Chỉ nhân viên đang hoạt động được đăng ký ca của mình.");
    }
    private String revision(Collection<UUID> ids) {
        try {
            String value = String.join(",", ids.stream().map(UUID::toString).sorted().toList());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
