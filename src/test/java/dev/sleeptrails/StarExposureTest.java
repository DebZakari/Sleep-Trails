package dev.sleeptrails;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StarExposureTest {
    @Test void exposureNeverReachesBeforeLocalSleepAndIsBounded() {
        assertEquals(0, StarExposure.width(true, 13149, 13149, 0));
        assertEquals(100, StarExposure.width(true, 13149, 13249, .1));
        assertEquals(1024, StarExposure.width(true, 13149, 18000, .5));
        assertEquals(0, StarExposure.width(true, 13149, 13000, .1));
    }
    @Test void exposureFadesContinuouslyAndInactiveAlwaysReturnsStock() {
        assertEquals(1024, StarExposure.width(true, 13149, 23800, .8));
        assertEquals(512, StarExposure.width(true, 13149, 23900, .9), .001);
        assertEquals(0, StarExposure.width(true, 13149, 24000, 1));
        assertEquals(0, StarExposure.width(false, 13149, 18000, .5));
    }
    @Test void arcFollowsTheStarAngleAcrossItsWrapAndNeverRunsBackwards() {
        assertEquals(Math.toRadians(15), StarExposure.arc(100, 85), 1e-6);
        assertEquals(Math.toRadians(20), StarExposure.arc(10, 350), 1e-6);
        assertEquals(Math.toRadians(18), StarExposure.arc(360, 342), 1e-6);
        assertEquals(0, StarExposure.arc(85, 100));
        assertEquals(0, StarExposure.arc(42, 42));
    }
}
