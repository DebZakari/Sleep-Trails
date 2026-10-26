package dev.sleeptrails;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkyTransitionTest {
    private static final long SECOND = 1_000_000_000L;
    private static final SkyTransition.Options ON = new SkyTransition.Options(true, true, true);

    /** The same observation-before-packet/frame order used by the Minecraft adapter. */
    private static final class Driver {
        final SkyTransition transition = new SkyTransition();
        Object world = new Object();
        boolean supported = true, sleeping;
        SkyTransition.Options options = ON;

        SkyTransition.Snapshot draw(long now, double time, double gameTime) {
            transition.observe(world, supported, sleeping, time, now, options);
            return transition.beginFrame(now, time, gameTime);
        }

        SkyTransition.Start update(long now, double before, double after, float rate) {
            transition.observe(world, supported, sleeping, before, now, options);
            return transition.timeUpdate(before, after, rate, now);
        }
    }

    @Test void localSleepPublishesSkyExposureCameraAndCloudsThroughTheFinish() {
        var d = new Driver();
        d.sleeping = true;
        var start = d.draw(0, 13000, 1000);
        assertEquals(13000, start.sky().time());
        assertEquals(0, start.exposure().ticks());
        assertEquals(0, start.previewSeconds());
        var held = d.draw(5 * SECOND, 13100, 1100);
        assertEquals(23500, held.sky().time());
        assertEquals(1024, held.exposure().ticks());
        assertEquals(SkyTransition.StartKind.CONFIRMED, d.update(5 * SECOND, 13100, 24000, 1).kind());
        assertSame(held.sky(), d.transition.snapshot().sky(), "Packets do not publish a new render frame");
        d.sleeping = false;
        var first = d.draw(5 * SECOND, 24000, 1100);
        assertEquals(held.sky().time(), first.sky().time());
        assertEquals(Double.POSITIVE_INFINITY, first.previewSeconds());
        for (int i = 1; i <= 40; i++) {
            var frame = d.draw(5 * SECOND + i * SECOND / 20, 24000 + i, 1100 + i);
            assertEquals(frame.sky().time() - 12000, 1100 + i + frame.cloudOffset(), 1e-8);
            assertEquals(frame.sky().generation(), frame.exposure().generation());
        }
        var end = d.transition.snapshot();
        assertFalse(end.sky().active());
        assertEquals(24040, end.sky().time());
        assertEquals(0, end.exposure().ticks());
        assertTrue(Double.isNaN(end.previewSeconds()));
        assertEquals(end.cloudOffset(), d.draw(8 * SECOND, 24060, 1160).cloudOffset());
    }

    @Test void aLateConfirmationKeepsTheVisibleSkyAndTheFadingExposure() {
        var d = new Driver();
        d.sleeping = true;
        d.draw(0, 12200, 0);
        d.draw(5 * SECOND, 12300, 100);
        d.sleeping = false;
        d.draw(5 * SECOND, 12300, 100);
        var eased = d.draw(6 * SECOND, 12320, 120);
        assertTrue(eased.returning());
        assertTrue(eased.exposure().ticks() > 0);
        var event = d.update(6 * SECOND, 12320, 24020, 1);
        assertEquals(SkyTransition.StartKind.CONFIRMED, event.kind());
        assertEquals(eased.sky().progress(), event.finishFrom());
        var first = d.draw(6 * SECOND, 24020, 120);
        assertEquals(eased.sky().time(), first.sky().time(), 1e-8);
        assertEquals(eased.exposure().ticks(), first.exposure().ticks(), 1e-5);
        float previous = first.exposure().ticks();
        for (int i = 1; i <= 120; i++) {
            var frame = d.draw(6 * SECOND + 2 * SECOND * i / 120, 24020 + i / 3.0, 120 + i / 3.0);
            assertTrue(frame.exposure().ticks() <= previous + 1e-4);
            previous = frame.exposure().ticks();
        }
        assertFalse(d.transition.snapshot().sky().active());
        assertEquals(0, previous);
    }

    @Test void bedReentryKeepsBothTheEasedBackSkyAndItsCurrentTrailWidth() {
        var d = new Driver();
        d.sleeping = true;
        d.draw(0, 13000, 0);
        d.draw(SECOND, 13020, 20);
        d.sleeping = false;
        d.draw(SECOND, 13020, 20);
        var eased = d.draw(2 * SECOND, 13040, 40);
        assertTrue(eased.returning());
        assertTrue(eased.exposure().ticks() > 0);
        d.sleeping = true;
        var reentered = d.draw(2 * SECOND, 13040, 40);
        assertEquals(eased.sky().time(), reentered.sky().time(), 1e-8);
        assertEquals(eased.exposure().ticks(), reentered.exposure().ticks(), 1e-5);
        assertEquals(0, reentered.previewSeconds());
    }

    @Test void awakeRetargetKeepsSkyMotionAndExposureWithoutStartingTheBedCamera() {
        var d = new Driver();
        assertEquals(SkyTransition.StartKind.AWAKE, d.update(0, 13000, 24000, 1).kind());
        d.draw(0, 24000, 0);
        var shown = d.draw(2 * SECOND, 24040, 40);
        assertTrue(shown.exposure().ticks() > 0);
        assertEquals(SkyTransition.StartKind.AWAKE, d.update(2 * SECOND, 24040, 37000, 1).kind());
        assertSame(shown.sky(), d.transition.snapshot().sky());
        var retargeted = d.draw(2 * SECOND, 37000, 40);
        assertEquals(shown.sky().time(), retargeted.sky().time(), 1e-8);
        assertEquals(shown.sky().motionTime(), retargeted.sky().motionTime(), 1e-8);
        assertEquals(shown.exposure().ticks(), retargeted.exposure().ticks(), 1e-5);
        assertTrue(Double.isNaN(retargeted.previewSeconds()));
        assertTrue(d.draw(5 * SECOND, 37060, 100).sky().active());
        assertFalse(d.draw(7 * SECOND, 37100, 140).sky().active());
    }

    @Test void effectAndTimeJumpChoicesLatchUntilIdleButTrailsLatchOnRetarget() {
        var d = new Driver();
        d.update(0, 13000, 24000, 1);
        d.draw(0, 24000, 0);
        d.options = new SkyTransition.Options(false, false, false);
        var running = d.draw(SECOND, 24020, 20);
        assertTrue(running.sky().active());
        assertTrue(running.exposure().ticks() > 0, "A mid-transition trails toggle waits for the next exposure start");
        assertNotNull(d.update(SECOND, 24020, 37000, 1), "The time-jump choice remains latched during retarget");
        assertEquals(0, d.draw(SECOND, 37000, 20).exposure().ticks());
        d.draw(6 * SECOND, 37100, 120);
        assertNull(d.update(7 * SECOND, 37120, 48000, 1));
        assertFalse(d.draw(7 * SECOND, 48000, 140).sky().active());
    }

    @Test void disablingAwakeJumpsStillAllowsTheLocalSleepPathAndItsTrailsChoice() {
        var d = new Driver();
        d.options = new SkyTransition.Options(true, false, false);
        assertNull(d.update(0, 13000, 24000, 1));
        d.sleeping = true;
        d.draw(SECOND, 13000, 20);
        var held = d.draw(6 * SECOND, 13100, 120);
        assertTrue(held.sky().active());
        assertEquals(23500, held.sky().time());
        assertEquals(0, held.exposure().ticks());
        assertEquals(SkyTransition.StartKind.CONFIRMED, d.update(6 * SECOND, 13100, 24000, 1).kind());
    }

    @Test void localSleepTakesOverAnAwakeTransitionWithoutLosingTheExposure() {
        var d = new Driver();
        d.update(0, 1000, 13000, 1);
        d.draw(0, 13000, 0);
        var awake = d.draw(2 * SECOND, 13040, 40);
        d.sleeping = true;
        var local = d.draw(2 * SECOND, 13040, 40);
        assertEquals(awake.sky().time(), local.sky().time(), 1e-8);
        assertEquals(awake.exposure().ticks(), local.exposure().ticks(), 1e-5);
        assertFalse(local.awake());
        assertEquals(0, local.previewSeconds());
    }

    @Test void cancellationPublishesZeroExposureImmediatelyAndKeepsCloudPositionWithoutRearmingBed() {
        var d = new Driver();
        d.sleeping = true;
        d.draw(0, 13000, 0);
        var active = d.draw(SECOND, 13020, 20);
        d.transition.cancel();
        var cancelled = d.transition.snapshot();
        assertFalse(cancelled.sky().active());
        assertEquals(0, cancelled.exposure().ticks());
        assertTrue(Double.isNaN(cancelled.previewSeconds()));
        assertTrue(cancelled.sky().generation() > active.sky().generation());
        assertEquals(active.cloudOffset(), cancelled.cloudOffset());
        var stillInBed = d.draw(2 * SECOND, 13040, 40);
        assertFalse(stillInBed.sky().active());
        assertEquals(active.cloudOffset(), stillInBed.cloudOffset());
        assertNull(d.update(3 * SECOND, 13060, 24000, 1));
        d.sleeping = false;
        d.draw(4 * SECOND, 24020, 80);
        d.sleeping = true;
        assertTrue(d.draw(5 * SECOND, 37000, 100).sky().active());
    }

    @Test void packetCancellationClearsExposureBeforeAnotherFrameAndDoesNotMoveCloudsAcrossTheJump() {
        var d = new Driver();
        d.update(0, 1000, 13000, 1);
        d.draw(0, 13000, 0);
        var active = d.draw(SECOND, 13020, 20);
        assertNull(d.update(SECOND, 13020, 23000, 1));
        assertEquals(0, d.transition.snapshot().exposure().ticks());
        assertSame(active.sky(), d.transition.snapshot().sky());
        var cancelled = d.draw(SECOND, 23000, 20);
        assertFalse(cancelled.sky().active());
        assertEquals(active.cloudOffset(), cancelled.cloudOffset());
        assertTrue(cancelled.sky().generation() > active.sky().generation());
    }

    @Test void aWorldChangeClearsDriftAndExposureAndAllowsANewLocalSleep() {
        var d = new Driver();
        d.sleeping = true;
        d.draw(0, 13000, 0);
        var old = d.draw(SECOND, 13020, 20);
        assertTrue(old.cloudOffset() > 0);
        d.world = new Object();
        d.sleeping = false;
        var fresh = d.draw(2 * SECOND, 6000, 0);
        assertFalse(fresh.sky().active());
        assertEquals(0, fresh.exposure().ticks());
        assertEquals(0, fresh.cloudOffset());
        assertTrue(fresh.sky().generation() > old.sky().generation());
        d.sleeping = true;
        assertEquals(0, d.draw(3 * SECOND, 13000, 20).exposure().ticks());
    }

    @Test void unsupportedOrDisconnectedContextsAndRateChangesNeverLeaveExposureRunning() {
        var d = new Driver();
        d.update(0, 1000, 13000, 1);
        d.draw(0, 13000, 0);
        d.draw(SECOND, 13020, 20);
        assertNull(d.update(SECOND, 13020, 13020, 0));
        assertEquals(0, d.draw(SECOND, 13020, 20).exposure().ticks());
        d.supported = false;
        assertNull(d.update(2 * SECOND, 13020, 24000, 1));
        assertFalse(d.draw(2 * SECOND, 24000, 40).sky().active());
        d.world = null;
        var disconnected = d.draw(3 * SECOND, 0, 0);
        assertFalse(disconnected.sky().active());
        assertEquals(0, disconnected.exposure().ticks());
        assertEquals(0, disconnected.cloudOffset());
    }

    @Test void shaderEpochReconciliationDoesNotBecomeCloudTravelOrExposureWidth() {
        var d = new Driver();
        d.update(0, 143000, 0, 1);
        var start = d.draw(0, 0, 0);
        assertEquals(143000, start.sky().time());
        assertEquals(143000, start.shaderTime(0));
        var end = d.draw(5 * SECOND, 100, 100);
        assertFalse(end.sky().active());
        assertTrue(end.shiftedEpoch());
        assertEquals(144100, end.shaderTime(100));
        assertEquals(1000, end.cloudOffset(), 1e-8);
        var day = d.draw(300 * SECOND, 6000, 6000);
        assertFalse(day.shiftedEpoch());
        assertEquals(6000, day.shaderTime(6000));
        assertEquals(5900, day.sky().motionTime() - end.sky().motionTime());
        assertEquals(end.cloudOffset(), day.cloudOffset());
        assertEquals(0, day.exposure().ticks());
    }

    @Test void exposureModulusUsesPresentedTimeAndIndependentTransitionsHaveIndependentHistory() {
        var a = new Driver();
        a.sleeping = true;
        a.draw(0, 99 * 24000 + 13000, 0);
        a.draw(5 * SECOND, 99 * 24000 + 13100, 100);
        a.update(5 * SECOND, 99 * 24000 + 13100, 100 * 24000, 1);
        var shown = a.draw(6 * SECOND, 100 * 24000 + 20, 120);
        assertEquals(Math.floorMod((long)Math.floor(shown.sky().time()), 2_400_000L), shown.exposure().clockMod());
        assertEquals(shown.sky().generation(), shown.exposure().generation());
        assertTrue(shown.exposure().ticks() > 0);
        var b = new Driver();
        var other = b.draw(0, 6000, 0);
        assertEquals(0, other.exposure().ticks());
        assertEquals(0, other.cloudOffset());
        assertEquals(shown, a.transition.snapshot());
    }
}
