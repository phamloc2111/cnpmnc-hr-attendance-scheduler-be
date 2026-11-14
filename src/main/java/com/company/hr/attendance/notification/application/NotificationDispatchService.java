package com.company.hr.attendance.notification.application;

import com.company.hr.attendance.identity.IdentityApi;
import com.company.hr.attendance.notification.NotificationApi;
import com.company.hr.attendance.notification.infrastructure.PushRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatchService implements NotificationApi {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);
    private final PushRepository pushRepository;
    private final PushGateway pushGateway;
    private final IdentityApi identity;

    public NotificationDispatchService(PushRepository pushRepository, PushGateway pushGateway, IdentityApi identity) {
        this.pushRepository = pushRepository;
        this.pushGateway = pushGateway;
        this.identity = identity;
    }

    @Override
    public int pushToUsers(Collection<UUID> userIds, String title, String body, String tag, Map<String, String> data, Instant expiresAt) {
        if (userIds == null || userIds.isEmpty()) return 0;
        var devices = pushRepository.devicesForUsers(userIds);
        if (devices.isEmpty() || !pushGateway.available()) return 0;
        Instant now = Instant.now();
        int sent = 0;
        for (var device : devices) {
            try {
                var person = identity.person(device.employeeId());
                if (person.locked()) continue;
                var result = pushGateway.sendCustom(device.employeeId(), device.token(), title, body, tag, data, expiresAt, now);
                if (result == PushGateway.Result.SENT) {
                    sent++;
                } else if (result == PushGateway.Result.INVALID_TOKEN) {
                    pushRepository.remove(device.id());
                }
            } catch (RuntimeException ex) {
                log.warn("Best-effort push dispatch failed for device {}: {}", device.id(), ex.getMessage());
            }
        }
        return sent;
    }
}
