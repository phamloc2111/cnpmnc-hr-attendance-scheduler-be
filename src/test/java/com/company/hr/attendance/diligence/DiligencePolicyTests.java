package com.company.hr.attendance.diligence;
import com.company.hr.attendance.diligence.domain.DiligencePolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class DiligencePolicyTests {
    final Instant start=Instant.parse("2026-11-01T01:00:00Z");final DiligencePolicy policy=new DiligencePolicy(5);
    @Test void graceAndExactFifteenAndThirtyMinuteBoundariesUseUnroundedDurations(){
        assertThat(policy.penalty(start,start.minusSeconds(60))).isZero();
        assertThat(policy.penalty(start,start.plusSeconds(300))).isZero();
        assertThat(policy.penalty(start,start.plusSeconds(300).plusNanos(1))).isEqualTo(5);
        assertThat(policy.penalty(start,start.plusSeconds(900).minusNanos(1))).isEqualTo(5);
        assertThat(policy.penalty(start,start.plusSeconds(900))).isEqualTo(10);
        assertThat(policy.penalty(start,start.plusSeconds(1800).minusNanos(1))).isEqualTo(10);
        assertThat(policy.penalty(start,start.plusSeconds(1800))).isEqualTo(20);
    }
    @Test void scoreClampsAtZeroAndNinetyStillQualifies(){
        assertThat(policy.score(0)).isEqualTo(100);assertThat(policy.score(10)).isEqualTo(90);
        assertThat(policy.score(30)).isEqualTo(70);assertThat(policy.score(120)).isZero();
    }
    @Test void displayedSecondsRoundUpWithoutChangingPenaltyTier(){
        assertThat(policy.lateSeconds(start,start.plusNanos(1))).isEqualTo(1);
        assertThat(policy.lateSeconds(start,start.minusSeconds(1))).isZero();
        assertThat(policy.lateSeconds(start,start.plusSeconds(899).plusNanos(1))).isEqualTo(900);
        assertThat(policy.penalty(start,start.plusSeconds(899).plusNanos(1))).isEqualTo(5);
    }
    @Test void zeroGraceIsSupportedButInvalidGraceFailsFast(){
        assertThat(new DiligencePolicy(0).penalty(start,start.plusNanos(1))).isEqualTo(5);
        assertThatThrownBy(()->new DiligencePolicy(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new DiligencePolicy(15)).isInstanceOf(IllegalArgumentException.class);
    }
}
