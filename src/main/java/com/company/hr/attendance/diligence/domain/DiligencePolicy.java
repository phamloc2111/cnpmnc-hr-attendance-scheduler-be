package com.company.hr.attendance.diligence.domain;

import java.time.Duration;
import java.time.Instant;

/** Score server-recorded check-in delay using exact duration, never rounded display minutes. */
public record DiligencePolicy(int graceMinutes) {
    public static final int INITIAL_SCORE = 100;
    public static final int REWARD_THRESHOLD = 90;
    public DiligencePolicy {
        if (graceMinutes < 0 || graceMinutes >= 15) throw new IllegalArgumentException("Diligence grace must be 0..14 minutes");
    }
    public int penalty(Instant startsAt, Instant checkInAt) {
        var late = Duration.between(startsAt, checkInAt);
        if (late.compareTo(Duration.ofMinutes(graceMinutes)) <= 0) return 0;
        if (late.compareTo(Duration.ofMinutes(15)) < 0) return 5;
        if (late.compareTo(Duration.ofMinutes(30)) < 0) return 10;
        return 20;
    }
    public long lateSeconds(Instant startsAt, Instant checkInAt) {
        var duration = Duration.between(startsAt, checkInAt);
        return duration.isNegative() ? 0 : duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0);
    }
    public int score(long deducted) { return (int)Math.max(0, INITIAL_SCORE - deducted); }
}
