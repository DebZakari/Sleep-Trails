package dev.sleeptrails;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresentationClockTest {
    private static final long SECOND = 1_000_000_000L;
    private static PresentationClock clock() {
        var clock = new PresentationClock(5 * SECOND, SECOND / 4, SECOND * 3 / 2);
        clock.context(new Object(), true);
        return clock;
    }
    private static void arm(PresentationClock clock) { clock.observeSleep(true, 13049, 0); }

    @Test void transitionAlreadyAdvancesWhileInBedAndNeverPredictsAnUnconfirmedMorning() {
        var clock = clock(); arm(clock);
        assertEquals(13049, clock.frame(0, 13049).time());
        var middle = clock.frame(SECOND * 5 / 2, 13099);
        assertTrue(middle.active());
        assertTrue(middle.time() > 18000 && middle.time() < 23500);
        assertEquals(23500, clock.frame(5 * SECOND, 13149).time());
        assertEquals(23500, clock.frame(60 * SECOND, 14249).time());
        assertFalse(clock.confirmed());
    }

    @Test void serverSkipContinuesFromTheVisibleNightInsteadOfImmediatelyShowingDay() {
        var clock = clock(); arm(clock);
        double visible = clock.frame(5 * SECOND, 13149).time();
        assertTrue(clock.update(13149, 24000, 1, 5 * SECOND));
        clock.observeSleep(false, 24000, 5 * SECOND + 400_000);
        assertEquals(visible, clock.frame(5 * SECOND, 24000).time());
        assertTrue(clock.confirmed());
        double previous = visible;
        for (int i = 1; i <= 120; i++) {
            var frame = clock.frame(5 * SECOND + 2 * SECOND * i / 120, 24000 + 40.0 * i / 120);
            assertTrue(frame.time() >= previous);
            previous = frame.time();
        }
        assertFalse(clock.frame(7 * SECOND, 24040).active());
        assertEquals(24040, previous);
    }

    @Test void lateSleepAlsoHasASmoothConfirmedFinish() {
        var clock = clock(); clock.observeSleep(true, 23049, 0);
        assertTrue(clock.update(23150, 24000, 1, 5 * SECOND));
        assertEquals(23500, clock.frame(5 * SECOND, 24000).time());
        assertEquals(23770, clock.frame(6 * SECOND, 24020).time(), .001);
    }

    @Test void wakeBeforeUpdateIsAcceptedOnlyInsideTheBoundedAssociation() {
        var clock = clock(); arm(clock);
        clock.observeSleep(false, 13149, 5 * SECOND);
        assertTrue(clock.update(13149, 24000, 1, 5 * SECOND + 100_000_000));
        assertFalse(clock.returning());
        // Past the wake allowance the ease-back has begun, and a skip still finishes from it until the late window closes.
        clock = clock(); arm(clock);
        clock.observeSleep(false, 13149, 5 * SECOND);
        assertTrue(clock.update(13149, 24000, 1, 5 * SECOND + 300_000_000));
        assertTrue(clock.confirmed() && !clock.returning());
        clock = clock(); arm(clock);
        clock.observeSleep(false, 13149, 5 * SECOND);
        assertFalse(clock.update(13179, 24030, 1, 6 * SECOND + 600_000_000));
        assertFalse(clock.frame(6 * SECOND + 600_000_000, 24030).active());
    }

    @Test void aSkipSentAfterTheWakeFinishesFromTheEasedBackSkyWithoutRegrowingTheTrails() {
        // 1.21.11 through ViaFabricPlus: the time comes once a second, here 1002 ms after the wake, 20 ticks past morning.
        var clock = clock(); clock.observeSleep(true, 12200, 0);
        long wake = 5 * SECOND, skip = wake + 1_002_000_000L;
        clock.observeSleep(false, 12300, wake);
        double previousTime = Double.MAX_VALUE, previousWidth = Double.MAX_VALUE;
        for (long now = wake + SECOND / 4 + SECOND / 60; now < skip; now += SECOND / 60) {
            var frame = clock.frame(now, 12305 + (now - wake) / 50e6);
            assertTrue(frame.active() && clock.returning());
            double width = StarExposure.width(true, 12200, frame.time(), frame.progress());
            assertTrue(frame.time() <= previousTime && width <= previousWidth);
            previousTime = frame.time(); previousWidth = width;
        }
        var eased = clock.frame(skip, 12325);
        double easedWidth = StarExposure.width(true, 12200, eased.time(), eased.progress());
        assertTrue(eased.time() > 17000 && eased.time() < 20000 && easedWidth > 400 && easedWidth < 700);
        assertTrue(clock.update(12325, 24020, 1, skip));
        assertTrue(clock.confirmed() && !clock.returning());
        assertEquals(eased.progress(), clock.finishFrom(), 1e-9);
        var first = clock.frame(skip, 24020);
        assertEquals(eased.time(), first.time(), 1e-6);
        assertEquals(eased.progress(), first.progress(), 1e-9);
        previousTime = first.time(); previousWidth = easedWidth;
        for (int i = 1; i <= 120; i++) {
            long now = skip + 2 * SECOND * i / 120;
            var frame = clock.frame(now, 24020 + 40.0 * i / 120);
            double width = StarExposure.width(frame.active(), 12200, frame.time(), frame.progress());
            assertTrue(frame.time() >= previousTime && width <= previousWidth + 1e-6);
            previousTime = frame.time(); previousWidth = width;
        }
        assertFalse(clock.frame(skip + 2 * SECOND, 24060).active());
        assertEquals(24060, previousTime);
    }

    @Test void aLateSkipMayLandPastMorningOnlyByTheTicksSinceTheWake() {
        // 400 ms after the wake is 8 ticks, so the target may sit up to 10 ticks past morning.
        var clock = clock(); arm(clock);
        clock.observeSleep(false, 13149, 5 * SECOND);
        assertTrue(clock.update(13157, 24010, 1, 5 * SECOND + 400_000_000));
        clock = clock(); arm(clock);
        clock.observeSleep(false, 13149, 5 * SECOND);
        long generation = clock.frame(5 * SECOND + 300_000_000, 13155).generation();
        assertFalse(clock.update(13157, 24011, 1, 5 * SECOND + 400_000_000));
        var snapped = clock.frame(5 * SECOND + 400_000_000, 24011);
        assertFalse(snapped.active());
        assertTrue(snapped.generation() > generation);
        // While still in bed the target stays within 2 ticks.
        clock = clock(); arm(clock);
        assertFalse(clock.update(13149, 24003, 1, 5 * SECOND));
    }

    @Test void leavingBedEasesTheSkyBackWithoutInventingMorning() {
        var clock = clock(); arm(clock);
        double shown = clock.frame(SECOND, 13069).time();
        assertTrue(shown > 13069 && shown < 24000);
        clock.observeSleep(false, 13069, SECOND);
        assertFalse(clock.update(13069, 13070, 1, SECOND));
        // The wake allowance ends at 1.25 s; the sky then eases back over 1.5 s from where it visibly was.
        long abort = SECOND + SECOND / 4;
        var held = clock.frame(abort, 13074);
        double previousLead = held.time() - 13074;
        double previousWidth = StarExposure.width(true, 13049, held.time(), held.progress());
        assertTrue(previousLead > shown - 13069);
        for (int i = 1; i < 150; i++) {
            double authoritative = 13074 + 30.0 * i / 150;
            var frame = clock.frame(abort + 1_500_000_000L * i / 150, authoritative);
            assertTrue(frame.active() && clock.returning() && !clock.confirmed());
            double lead = frame.time() - authoritative;
            double width = StarExposure.width(true, 13049, frame.time(), frame.progress());
            assertTrue(lead >= 0 && lead <= previousLead);
            assertTrue(width <= previousWidth);
            previousLead = lead; previousWidth = width;
        }
        var settled = clock.frame(abort + 1_500_000_000L, 13104);
        assertFalse(settled.active());
        assertEquals(13104, settled.time());
        assertFalse(clock.update(13104, 24000, 1, 3 * SECOND));
    }

    @Test void theEaseBackIsTheSameWhicheverSideNoticesTheAbortFirst() {
        var ticked = clock(); var drawn = clock(); arm(ticked); arm(drawn);
        ticked.observeSleep(false, 13069, SECOND); drawn.observeSleep(false, 13069, SECOND);
        ticked.observeSleep(false, 13079, 2 * SECOND);
        assertTrue(ticked.returning());
        assertEquals(ticked.frame(2 * SECOND, 13089), drawn.frame(2 * SECOND, 13089));
    }

    @Test void reenteringBedDuringTheEaseBackContinuesFromTheVisibleSky() {
        var clock = clock(); arm(clock);
        clock.observeSleep(false, 13069, SECOND);
        double easing = clock.frame(2 * SECOND, 13089).time();
        assertTrue(clock.returning());
        assertTrue(clock.observeSleep(true, 13089, 2 * SECOND));
        assertFalse(clock.returning());
        assertEquals(easing, clock.frame(2 * SECOND, 13089).time(), 1e-9);
        assertEquals(23500, clock.frame(7 * SECOND, 13189).time(), 1e-9);
        assertTrue(clock.update(13189, 24000, 1, 7 * SECOND));
    }

    @Test void discontinuitiesDuringTheEaseBackStillSnap() {
        var clock = clock(); arm(clock);
        clock.observeSleep(false, 13069, SECOND);
        long generation = clock.frame(2 * SECOND, 13089).generation();
        assertFalse(clock.update(13089, 18000, 1, 2 * SECOND));
        var snapped = clock.frame(2 * SECOND, 18000);
        assertFalse(snapped.active());
        assertEquals(18000, snapped.time());
        assertTrue(snapped.generation() > generation);
    }

    @Test void ordinaryRolloverAwakeCommandsAndDaytimeWeatherDoNotAnimate() {
        var clock = clock();
        assertFalse(clock.update(13050, 24000, 1, 0));
        assertFalse(clock.update(23999, 24000, 1, 0));
        clock.observeSleep(true, 6000, 0);
        assertFalse(clock.update(6000, 24000, 1, SECOND));
        assertFalse(clock.frame(SECOND, 24000).active());
    }

    @Test void rainEveningSleepBeforeTheClearSkyThresholdAnimates() {
        var clock = clock();
        assertTrue(clock.observeSleep(true, 12100, 0));
        assertTrue(clock.frame(SECOND, 12120).time() > 12120);
        assertTrue(clock.update(12200, 24000, 1, 5 * SECOND));
        assertTrue(clock.frame(6 * SECOND, 24020).active());
    }

    @Test void thunderSleepBeforeSunsetIsLeftToVanillaEvenIfTheSkipLandsAfterIt() {
        var clock = clock();
        assertFalse(clock.observeSleep(true, 11950, 0));
        assertFalse(clock.frame(SECOND, 11970).active());
        assertFalse(clock.update(12050, 24000, 1, 5 * SECOND));
        assertFalse(clock.frame(6 * SECOND, 24020).active());
    }

    @Test void duplicateAndNormalClockUpdatesDoNotRestartTheConfirmedFinish() {
        var clock = clock(); arm(clock);
        clock.update(13149, 24000, 1, 5 * SECOND);
        assertFalse(clock.update(24020, 24020, 1, 6 * SECOND));
        assertEquals(.875, clock.frame(6 * SECOND, 24020).progress(), .0001);
        assertFalse(clock.frame(8 * SECOND, 24060).active());
    }

    @Test void discontinuitiesCancelAndNeverReplayAnOldWorld() {
        var clock = clock(); arm(clock);
        clock.update(13149, 24000, 1, 5 * SECOND);
        clock.update(24020, 6000, 1, 6 * SECOND);
        assertFalse(clock.frame(6 * SECOND, 6000).active());
        long generation = clock.frame(6 * SECOND, 6000).generation();
        clock.context(new Object(), true);
        assertFalse(clock.frame(6 * SECOND, 500).active());
        assertTrue(clock.frame(6 * SECOND, 500).generation() > generation);
        assertFalse(clock.update(13149, 24000, 1, 6 * SECOND));
    }

    @Test void disablingOrUnsupportedWorldAndFixedClockNeverConfirm() {
        var clock = clock(); arm(clock);
        assertFalse(clock.update(13149, 24000, 0, 0));
        assertFalse(clock.frame(SECOND, 24000).active());
        clock.context(new Object(), false); arm(clock);
        assertFalse(clock.frame(SECOND, 13149).active());
    }

    @Test void frameRateAndStallsDoNotChangeElapsedProgress() {
        var sparse = clock(); var dense = clock(); arm(sparse); arm(dense);
        for (int i = 0; i < 240; i++) dense.frame(SECOND * i / 120, 13049 + i / 6.0);
        assertEquals(sparse.frame(2 * SECOND, 13089), dense.frame(2 * SECOND, 13089));
        sparse.update(13149, 24000, 1, 5 * SECOND);
        assertFalse(sparse.frame(30 * SECOND, 24500).active());
    }

    @Test void absoluteDayTimeIsNotWrappedAtDayOneHundred() {
        var clock = clock();
        clock.observeSleep(true, 99 * 24000 + 13049, 0);
        assertTrue(clock.update(99 * 24000 + 13149, 100 * 24000, 1, 5 * SECOND));
        assertTrue(clock.frame(6 * SECOND, 100 * 24000 + 20).time() > 99 * 24000);
    }

    @Test void cancellationInBedWaitsForANewSleepEntry() {
        var clock = clock(); arm(clock);
        long generation = clock.frame(SECOND, 13069).generation();
        clock.cancel();
        assertFalse(clock.observeSleep(true, 13089, 2 * SECOND));
        var cancelled = clock.frame(2 * SECOND, 13089);
        assertFalse(cancelled.active());
        assertEquals(13089, cancelled.time());
        assertTrue(cancelled.generation() > generation);
        assertFalse(clock.update(13149, 24000, 1, 5 * SECOND));
        clock.observeSleep(false, 24000, 5 * SECOND);
        assertTrue(clock.observeSleep(true, 37049, 10 * SECOND));
        assertTrue(clock.frame(11 * SECOND, 37069).active());
    }

    @Test void arbitraryCommandsDuringSleepCancelThePreview() {
        var clock = clock(); arm(clock);
        assertFalse(clock.update(13149, 18000, 1, SECOND));
        assertFalse(clock.frame(SECOND, 18000).active());
        assertFalse(clock.observeSleep(true, 18000, 2 * SECOND));
        assertFalse(clock.frame(2 * SECOND, 18000).active());
        assertFalse(clock.update(13149, 6000, 1, SECOND));
        assertFalse(clock.update(13149, 48000, 1, SECOND));
    }

    @Test void previewSecondsTimeTheInBedCameraUntilTheSkipAndStopForTheEaseBack() {
        var clock = clock();
        assertTrue(Double.isNaN(clock.previewSeconds(0)));
        arm(clock);
        clock.frame(SECOND * 3 / 2, 13079);
        assertEquals(1.5, clock.previewSeconds(SECOND * 3 / 2), 1e-9);
        assertTrue(clock.update(13149, 24000, 1, 5 * SECOND));
        assertEquals(Double.POSITIVE_INFINITY, clock.previewSeconds(5 * SECOND));

        var aborted = clock(); arm(aborted);
        aborted.observeSleep(false, 13060, SECOND);
        aborted.frame(2 * SECOND, 13080);
        assertTrue(aborted.returning());
        assertTrue(Double.isNaN(aborted.previewSeconds(2 * SECOND)));
    }
}
