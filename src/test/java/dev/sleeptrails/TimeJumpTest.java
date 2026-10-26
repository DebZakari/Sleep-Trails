package dev.sleeptrails;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimeJumpTest {
    private static final long SECOND = 1_000_000_000L;
    private static PresentationClock clock() {
        var clock = new PresentationClock(5 * SECOND, SECOND / 4, SECOND * 3 / 2);
        clock.context(new Object(), true);
        return clock;
    }

    @Test void anotherPlayersSkipStartsFromNightAndCatchesUpAfterFiveSeconds() {
        var clock = clock();
        assertTrue(clock.update(13000, 24000, 1, 0, true));
        assertEquals(13000, clock.frame(0, 24000).time(), 1e-8);
        assertEquals(18550, clock.frame(SECOND * 5 / 2, 24050).time(), 1e-8);
        var end = clock.frame(5 * SECOND, 24100);
        assertFalse(end.active());
        assertEquals(24100, end.time(), 1e-8);
        assertTrue(Double.isNaN(clock.previewSeconds(SECOND)));
    }

    @Test void commandsThatResetTheDayNumberStillTravelForwardAndEndAtServerTime() {
        var day = clock();
        assertTrue(day.update(121000, 13000, 1, 0, true));
        assertEquals(1000, phase(day.frame(0, 13000).time()), 1e-8);
        assertEquals(7050, phase(day.frame(SECOND * 5 / 2, 13050).time()), 1e-8);
        assertEquals(13100, day.frame(5 * SECOND, 13100).time(), 1e-8);

        var night = clock();
        assertTrue(night.update(143000, 1000, 1, 0, true));
        assertEquals(23000, phase(night.frame(0, 1000).time()), 1e-8);
        assertEquals(50, phase(night.frame(SECOND * 5 / 2, 1050).time()), 1e-8);
        assertEquals(1100, phase(night.frame(5 * SECOND, 1100).time()), 1e-8);
        assertEquals(6000, night.frame(300 * SECOND, 6000).time());
    }

    @Test void aSecondJumpRetargetsFromTheVisibleSkyWithoutQueuing() {
        var clock = clock();
        clock.update(13000, 24000, 1, 0, true);
        var shown = clock.frame(2 * SECOND, 24040);
        assertTrue(clock.update(24040, 37000, 1, 2 * SECOND, true));
        assertEquals(shown.time(), clock.frame(2 * SECOND, 37000).time(), 1e-8);
        assertEquals(shown.motionTime(), clock.frame(2 * SECOND, 37000).motionTime(), 1e-8);
        assertFalse(clock.update(37020, 37020, 1, 3 * SECOND, true));
        assertTrue(clock.frame(5 * SECOND, 37060).active());
        assertFalse(clock.frame(7 * SECOND, 37100).active());
        assertEquals(37100, clock.frame(7 * SECOND, 37100).time(), 1e-8);
    }

    @Test void ordinaryTimeSmallCorrectionsAndSameHalfDayCommandsStayInactive() {
        for (double[] pair : new double[][] {{23999, 24000}, {11999, 12000}, {12001, 11999},
            {23995, 24005}, {1000, 6000}, {13000, 23000}, {13000, 37000}}) {
            var clock = clock();
            assertFalse(clock.update(pair[0], pair[1], 1, 0, true));
            assertFalse(clock.frame(SECOND, pair[1] + 20).active());
        }
    }

    @Test void disabledAndPausedClocksNeverStartAndRateChangesCancel() {
        assertFalse(clock().update(13000, 24000, 1, 0, false));
        assertFalse(clock().update(13000, 24000, 0, 0, true));
        var disabled = new PresentationClock(5 * SECOND, SECOND / 4, SECOND * 3 / 2);
        assertFalse(disabled.update(13000, 24000, 1, 0, true));
        var clock = clock();
        clock.update(1000, 13000, 1, 0, true);
        assertFalse(clock.update(13020, 13020, 0, SECOND, true));
        assertFalse(clock.frame(SECOND, 13020).active());
    }

    @Test void unrelatedDiscontinuitiesAndWorldChangesClearTheOldTarget() {
        var clock = clock();
        clock.update(1000, 13000, 1, 0, true);
        clock.update(13020, 23000, 1, SECOND, true);
        assertFalse(clock.frame(SECOND, 23000).active());
        clock.update(23000, 24000, 1, 2 * SECOND, true);
        long generation = clock.frame(2 * SECOND, 24000).generation();
        clock.context(new Object(), true);
        assertFalse(clock.frame(3 * SECOND, 1000).active());
        assertTrue(clock.frame(3 * SECOND, 1000).generation() > generation);
        assertEquals(1000, clock.frame(10 * SECOND, 1000).time());
    }

    @Test void enteringBedTakesOverFromTheVisibleSkyWithTheOriginalSleepTiming() {
        var clock = clock();
        clock.update(1000, 13000, 1, 0, true);
        double visible = clock.frame(2 * SECOND, 13040).time();
        assertTrue(clock.observeSleep(true, 13040, 2 * SECOND));
        assertFalse(clock.awake());
        assertEquals(visible, clock.frame(2 * SECOND, 13040).time(), 1e-8);
        assertEquals(0, clock.previewSeconds(2 * SECOND));
        assertTrue(clock.update(13140, 24000, 1, 7 * SECOND, true));
        assertTrue(clock.confirmed());
        assertFalse(clock.frame(9 * SECOND, 24040).active());
    }

    @Test void lateLocalPacketsAreNotReinterpretedAsAwakeTransitions() {
        var clock = clock();
        clock.observeSleep(true, 13000, 0);
        clock.observeSleep(false, 13100, 5 * SECOND);
        assertTrue(clock.update(13120, 24020, 1, 6 * SECOND, true));
        assertTrue(clock.confirmed());
        assertFalse(clock.awake());
        assertFalse(clock.frame(8 * SECOND, 24060).active());
    }

    @Test void epochReconciliationWaitsUntilDaylightAcrossRapidRetargets() {
        var clock = clock();
        clock.update(2390000, 1000, 1, 0, true); // visible night on day 99; server resets to day 0.
        var first = clock.frame(0, 1000);
        assertEquals(2390000, first.time());
        assertTrue(clock.shiftedEpoch());
        clock.update(1000, 18000, 1, 0, true); // still showing night, so do not switch its star field.
        assertEquals(first.time(), clock.frame(0, 18000).time());
        double motion = clock.frame(SECOND, 18020).motionTime();
        assertTrue(motion > first.motionTime());
        clock.frame(5 * SECOND, 18100);
        // This retarget stays within the visible night. Keep its star field until natural daylight hides the rebase.
        assertEquals(18100, phase(clock.frame(5 * SECOND, 18100).time()));
        assertTrue(clock.shiftedEpoch());
        assertEquals(30000, clock.frame(700 * SECOND, 30000).time());
        assertFalse(clock.shiftedEpoch());
    }

    @Test void aDawnTargetKeepsTheVisibleStarFieldUntilFullDaylight() {
        var clock = clock();
        clock.update(143000, 0, 1, 0, true);
        var end = clock.frame(5 * SECOND, 100);
        assertFalse(end.active());
        assertEquals(100, phase(end.time()));
        assertTrue(clock.shiftedEpoch(), "Dawn still has visible stars; do not change their absolute field yet");
        double motion = end.motionTime();
        var daylight = clock.frame(300 * SECOND, 6000);
        assertFalse(clock.shiftedEpoch());
        assertEquals(6000, daylight.time());
        assertEquals(5900, daylight.motionTime() - motion);
    }

    private static double phase(double time) { return time - Math.floor(time / 24000) * 24000; }
}
