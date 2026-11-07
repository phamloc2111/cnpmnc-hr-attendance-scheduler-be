package com.company.hr.attendance.attendance;

import com.company.hr.attendance.attendance.domain.AttendanceClassifier;
import com.company.hr.attendance.attendance.domain.AttendanceClassifier.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AttendanceClassifierTest {
    private static final Instant START = Instant.parse("2026-11-05T01:00:00Z"); // 08:00 Vietnam
    private static final Instant END = Instant.parse("2026-11-05T10:00:00Z");

    @Test void fourMinutesIsOnTimeAndSixMinutesIsLate() {
        var onTime = AttendanceClassifier.classify(START, END, START.plusSeconds(4 * 60), END, END);
        var late = AttendanceClassifier.classify(START, END, START.plusSeconds(6 * 60), END, END);
        assertEquals(Arrival.ON_TIME, onTime.arrival());
        assertEquals(Overall.ON_TIME, onTime.overall());
        assertEquals(Arrival.LATE, late.arrival());
        assertEquals(6, late.lateMinutes());
        assertEquals(Overall.LATE, late.overall());
    }
    @Test void absentOnlyAfterShiftEndWithoutCheckIn() {
        assertEquals(Overall.PENDING, AttendanceClassifier.classify(START, END, null, null, END.minusSeconds(1)).overall());
        assertEquals(Overall.ABSENT, AttendanceClassifier.classify(START, END, null, null, END).overall());
    }
    @Test void checkoutOverFiveMinutesEarlyIsFlaggedAndBothViolationsSurvive() {
        var result = AttendanceClassifier.classify(START, END, START.plusSeconds(6 * 60), END.minusSeconds(6 * 60), END);
        assertEquals(Departure.EARLY_LEAVE, result.departure());
        assertEquals(6, result.earlyMinutes());
        assertEquals(Overall.LATE_AND_EARLY_LEAVE, result.overall());
        assertEquals(Departure.ON_TIME, AttendanceClassifier.classify(START, END, START, END.minusSeconds(5 * 60), END).departure());
    }
    @Test void missingCheckOutDoesNotBecomeAbsent() {
        var result = AttendanceClassifier.classify(START, END, START, null, END);
        assertEquals(Arrival.ON_TIME, result.arrival());
        assertEquals(Overall.MISSING_CHECK_OUT, result.overall());
    }
}
