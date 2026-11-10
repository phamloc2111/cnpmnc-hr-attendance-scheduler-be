package com.company.hr.attendance.notification.infrastructure;

import com.company.hr.attendance.notification.application.PushGateway;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.*;
import com.google.firebase.messaging.*;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FirebasePushGateway implements PushGateway {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(FirebasePushGateway.class);
    private final FirebaseApp app;
    public FirebasePushGateway(@Value("${app.notifications.enabled:false}") boolean enabled,
                               @Value("${app.notifications.credentials-path:}") String credentialsPath) {
        if (!enabled) { app = null; return; }
        if (credentialsPath.isBlank()) throw new IllegalStateException("PUSH_ENABLED requires FIREBASE_CREDENTIALS_PATH outside the repository");
        try (var stream = Files.newInputStream(Path.of(credentialsPath))) {
            var credentials = ServiceAccountCredentials.fromStream(stream);
            app = FirebaseApp.initializeApp(FirebaseOptions.builder().setCredentials(credentials)
                .setProjectId(credentials.getProjectId()).setConnectTimeout(5000).setReadTimeout(10000).build(), "attendance-push");
        } catch (IOException error) { throw new IllegalStateException("Cannot load Firebase service account; check the configured private file and mount"); }
    }
    public boolean available() { return app != null; }
    public Result send(java.util.UUID employeeId, String token, LocalDate week, String kind, Instant expiresAt, Instant now) {
        if (app == null) return Result.RETRY;
        long ttl = Math.max(0, Duration.between(now, expiresAt).getSeconds());
        if (ttl == 0) return Result.RETRY;
        var message = Message.builder().setToken(token)
            .putData("employeeId", employeeId.toString()).putData("expiresAt", expiresAt.toString())
            .putData("title", kind.equals("TEST") ? "Đã bật thông báo đăng ký ca" : kind.equals("OPEN") ? "Đã mở đăng ký ca tuần tới" : "Bạn chưa đăng ký ca tuần tới")
            .putData("body", kind.equals("TEST") ? "Bạn sẽ nhận lời nhắc đăng ký ca vào Chủ nhật." : kind.equals("OPEN") ? "Chọn ca mong muốn trước 23:59 Chủ nhật (giờ Việt Nam)." : "Bạn chưa chọn ca tuần tới. Hãy đăng ký trước 23:59 Chủ nhật.")
            .putData("weekStart", week.toString()).putData("tag", "registration-" + kind.toLowerCase() + "-" + week)
            .setWebpushConfig(WebpushConfig.builder().putHeader("TTL", Long.toString(ttl)).putHeader("Urgency", "normal").build()).build();
        try { FirebaseMessaging.getInstance(app).send(message); return Result.SENT; }
        catch (FirebaseMessagingException error) {
            log.warn("FCM rejected a delivery: {}",error.getMessagingErrorCode());
            // Never log the exception text: provider messages may contain device tokens.
            return error.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED ? Result.INVALID_TOKEN : Result.RETRY;
        }
    }
    @PreDestroy void close() { if (app != null) app.delete(); }
}
