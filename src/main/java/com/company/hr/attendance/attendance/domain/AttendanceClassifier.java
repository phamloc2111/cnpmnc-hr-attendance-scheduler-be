package com.company.hr.attendance.attendance.domain;

import java.time.Duration;
import java.time.Instant;

/** Classifies server-recorded attendance times against the assigned shift. */
public final class AttendanceClassifier {
    private AttendanceClassifier() {}
    public enum Arrival { PENDING, ON_TIME, LATE, ABSENT }
    public enum Departure { PENDING, ON_TIME, EARLY_LEAVE, MISSING_CHECK_OUT }
    public enum Overall { PENDING, IN_PROGRESS, ON_TIME, LATE, EARLY_LEAVE, LATE_AND_EARLY_LEAVE, ABSENT, MISSING_CHECK_OUT }
    public record Result(Arrival arrival, Departure departure, Overall overall, long lateMinutes, long earlyMinutes) {}

    public static Result classify(Instant start, Instant end, Instant checkIn, Instant checkOut, Instant now) {
        return classify(start, end, checkIn, checkOut, now, 5);
    }
    public static Result classify(Instant start, Instant end, Instant checkIn, Instant checkOut, Instant now, int graceMinutes) {
        if (checkIn == null) {
            boolean absent = !now.isBefore(end);
            return new Result(absent ? Arrival.ABSENT : Arrival.PENDING, Departure.PENDING,
                absent ? Overall.ABSENT : Overall.PENDING, 0, 0);
        }
        boolean late = checkIn.isAfter(start.plus(Duration.ofMinutes(graceMinutes)));
        long lateMinutes = late ? ceilingMinutes(Duration.between(start, checkIn)) : 0;
        if (checkOut == null) {
            boolean missing = !now.isBefore(end);
            return new Result(late ? Arrival.LATE : Arrival.ON_TIME,
                missing ? Departure.MISSING_CHECK_OUT : Departure.PENDING,
                missing ? Overall.MISSING_CHECK_OUT : Overall.IN_PROGRESS, lateMinutes, 0);
        }
        boolean early = checkOut.isBefore(end.minus(Duration.ofMinutes(graceMinutes)));
        long earlyMinutes = early ? ceilingMinutes(Duration.between(checkOut, end)) : 0;
        Overall overall = late && early ? Overall.LATE_AND_EARLY_LEAVE : late ? Overall.LATE :
            early ? Overall.EARLY_LEAVE : Overall.ON_TIME;
        return new Result(late ? Arrival.LATE : Arrival.ON_TIME,
            early ? Departure.EARLY_LEAVE : Departure.ON_TIME, overall, lateMinutes, earlyMinutes);
    }
    private static long ceilingMinutes(Duration duration) {
        long wholeMinutes = duration.toMinutes();
        return Math.max(0, wholeMinutes + (duration.minusMinutes(wholeMinutes).isZero() ? 0 : 1));
    }
}
