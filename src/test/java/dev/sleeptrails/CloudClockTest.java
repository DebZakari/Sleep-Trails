package dev.sleeptrails;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudClockTest {
    private static PresentationClock.Frame frame(boolean active, double time, long generation) {
        return new PresentationClock.Frame(active, time, active ? .5 : 1, generation);
    }

    @Test void ordinaryPlayAndAnAwakeTimeCommandLeaveCloudsOnVanillaTime() {
        var clouds = new CloudClock();
        clouds.update(100, frame(false, 13000, 1));
        clouds.update(120, frame(false, 24000, 1));
        clouds.update(140, frame(false, 10, 2));
        assertEquals(0, clouds.offset());
    }

    @Test void cloudMotionFollowsTheRealSleepControllerThroughTheServerSkipAndFinish() {
        var sky = new PresentationClock(5_000_000_000L, 250_000_000L, 1_500_000_000L);
        sky.context(new Object(), true);
        var clouds = new CloudClock();
        clouds.update(1000, sky.frame(0, 13000));
        sky.observeSleep(true, 13000, 0);
        clouds.update(1000, sky.frame(0, 13000));
        for (int i = 1; i <= 100; i++) {
            var frame = sky.frame(i * 50_000_000L, 13000 + i);
            clouds.update(1000 + i, frame);
            assertEquals(frame.time() - 12000, 1000 + i + clouds.offset(), 1e-8);
        }
        assertTrue(sky.update(13100, 24000, 1, 5_000_000_000L));
        clouds.update(1100, sky.frame(5_000_000_000L, 24000));
        assertEquals(11500, 1100 + clouds.offset(), 1e-8);
        for (int i = 1; i <= 40; i++) {
            var frame = sky.frame(5_000_000_000L + i * 50_000_000L, 24000 + i);
            clouds.update(1100 + i, frame);
            assertEquals(frame.time() - 12000, 1100 + i + clouds.offset(), 1e-8);
        }
        double end = 1140 + clouds.offset();
        clouds.update(1160, sky.frame(8_000_000_000L, 24060));
        assertEquals(end + 20, 1160 + clouds.offset(), 1e-8);
    }

    @Test void interruptedSleepCanEaseBackWithoutRestoringTheOldCloudPositionAbruptly() {
        var clouds = new CloudClock();
        clouds.update(100, frame(true, 13000, 1));
        clouds.update(150, frame(true, 18000, 1));
        clouds.update(160, frame(true, 16000, 1));
        assertEquals(3100, 160 + clouds.offset());
        clouds.update(180, frame(false, 13080, 1));
        assertEquals(180, 180 + clouds.offset());
        clouds.update(200, frame(false, 13100, 1));
        assertEquals(200, 200 + clouds.offset());
    }

    @Test void cancellationOrReloadKeepsTheCurrentPositionAndDoesNotIntegrateTheClockJump() {
        var clouds = new CloudClock();
        clouds.update(100, frame(true, 13000, 1));
        clouds.update(150, frame(true, 18000, 1));
        double before = 150 + clouds.offset();
        clouds.update(151, frame(false, 24000, 2));
        assertEquals(before + 1, 151 + clouds.offset());
        clouds.update(152, frame(false, 24001, 2));
        assertEquals(before + 2, 152 + clouds.offset());
    }

    @Test void successiveSleepsAccumulateDriftAndAWorldChangeStartsFresh() {
        var clouds = new CloudClock();
        clouds.update(100, frame(true, 13000, 1));
        clouds.update(200, frame(false, 24000, 1));
        double firstOffset = clouds.offset();
        clouds.update(300, frame(true, 37000, 1));
        clouds.update(400, frame(false, 48000, 1));
        assertEquals(firstOffset * 2, clouds.offset());
        clouds.reset();
        clouds.update(10, frame(false, 6000, 2));
        assertEquals(0, clouds.offset());
    }
}
