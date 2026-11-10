package com.company.hr.attendance.notification.api;

import com.company.hr.attendance.notification.application.PushGateway;
import com.company.hr.attendance.notification.application.PushSubscriptionService;
import com.company.hr.attendance.shared.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasRole('EMPLOYEE')")
public class PushController {
    public record Device(@NotBlank @Size(max=4096) @Pattern(regexp="[A-Za-z0-9_:\\-]+") String token) {}
    public record Config(boolean enabled,String reminderTime,String timezone) {}
    private final PushSubscriptionService service;
    private final PushGateway gateway;
    private final String reminderTime;
    public PushController(PushSubscriptionService service,PushGateway gateway,@Value("${app.notifications.reminder-time:22:00}") String reminderTime) {
        this.service=service;this.gateway=gateway;this.reminderTime=reminderTime;
    }
    @GetMapping("/config") public ResponseEntity<Config> config() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Config(gateway.available(),reminderTime,"Asia/Ho_Chi_Minh")); }
    @PutMapping("/devices") public ResponseEntity<Void> subscribe(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Device device) {
        service.subscribe(UUID.fromString(jwt.getSubject()),UUID.fromString(jwt.getId()),device.token());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @DeleteMapping("/devices") public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Device device) {
        service.unsubscribe(UUID.fromString(jwt.getSubject()),device.token());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @PostMapping("/test") public ResponseEntity<Void> test(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Device device) {
        service.test(UUID.fromString(jwt.getSubject()),device.token());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @ExceptionHandler(PushSubscriptionService.PushDeliveryException.class)
    ResponseEntity<ApiError> deliveryFailed(HttpServletRequest request) { return ResponseEntity.status(502).cacheControl(CacheControl.noStore()).body(
        ApiError.of("PUSH_DELIVERY_FAILED","Chưa gửi được thông báo thử. Kiểm tra Firebase project, quyền Cloud Messaging và kết nối máy chủ.",request.getRequestURI())); }
    @ExceptionHandler(PushSubscriptionService.PushDeviceLimitException.class)
    ResponseEntity<ApiError> limit(HttpServletRequest request) { return ResponseEntity.status(409).cacheControl(CacheControl.noStore()).body(
        ApiError.of("PUSH_DEVICE_LIMIT","Tối đa 10 thiết bị nhận thông báo. Hãy tắt thông báo trên thiết bị cũ trước.",request.getRequestURI())); }
    @ExceptionHandler(PushSubscriptionService.PushNotConfiguredException.class)
    ResponseEntity<ApiError> unavailable(HttpServletRequest request) { return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
        .body(ApiError.of("PUSH_NOT_CONFIGURED","Thông báo đẩy chưa được cấu hình trên máy chủ.",request.getRequestURI())); }
}
