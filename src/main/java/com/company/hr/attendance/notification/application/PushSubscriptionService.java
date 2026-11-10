package com.company.hr.attendance.notification.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.notification.infrastructure.PushRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushSubscriptionService {
    private final IdentityApi identity;
    private final PushRepository repository;
    private final PushGateway gateway;
    public PushSubscriptionService(IdentityApi identity, PushRepository repository, PushGateway gateway) { this.identity=identity;this.repository=repository;this.gateway=gateway; }
    @Transactional
    public void subscribe(UUID employee, UUID session, String token) {
        var person=identity.lockPerson(employee);
        if (person.locked() || !person.role().equals("EMPLOYEE")) throw new AccessDeniedException("Employee required");
        if (!gateway.available()) throw new PushNotConfiguredException();
        var hash=hash(token);
        if (!repository.owns(employee,hash) && repository.deviceCount(employee)>=10) throw new PushDeviceLimitException();
        repository.subscribe(employee,session,token,hash,Instant.now());
    }
    @Transactional
    public void unsubscribe(UUID employee, String token) { identity.lockPerson(employee);repository.unsubscribe(employee,hash(token)); }
    public void test(UUID employee, String token) {
        if (!gateway.available()) throw new PushNotConfiguredException();
        if (!repository.owns(employee,hash(token))) throw new AccessDeniedException("Device unavailable");
        var now=Instant.now();var week=now.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()
            .with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
        if (gateway.send(employee,token,week,"TEST",now.plusSeconds(300),now)!=PushGateway.Result.SENT) throw new PushDeliveryException();
    }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public static class PushDeliveryException extends RuntimeException {}
    public static class PushDeviceLimitException extends RuntimeException {}
    public static class PushNotConfiguredException extends RuntimeException {}
}
