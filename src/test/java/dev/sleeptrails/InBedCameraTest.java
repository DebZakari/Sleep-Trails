package dev.sleeptrails;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InBedCameraTest {
    private static final Settings.Values VANILLA = Settings.Values.DEFAULTS;
    private static final Settings.Values CINEMATIC = VANILLA.withCamera(Settings.Camera.CINEMATIC);

    @Test void autoTiltLeavesTheFrameBottomTenDegreesBelowTheHorizon() {
        assertEquals(25, InBedCamera.tilt(Settings.AUTO, 70));
        assertEquals(35, InBedCamera.tilt(Settings.AUTO, 90));
        assertEquals(45, InBedCamera.tilt(Settings.AUTO, 110));
        assertEquals(0, InBedCamera.tilt(Settings.AUTO, 20));
        assertEquals(12, InBedCamera.tilt(12, 90));
    }

    @Test void vanillaModeEasesItsTiltInOverASecondAndZeroIsVanilla() {
        var auto = VANILLA.withTilt(Settings.AUTO);
        assertEquals(0, InBedCamera.firstPersonPitch(auto, 0, 90), 0);
        assertEquals(-17.5, InBedCamera.firstPersonPitch(auto, .5, 90), 1e-4);
        assertEquals(-35, InBedCamera.firstPersonPitch(auto, 1, 90));
        assertEquals(-35, InBedCamera.firstPersonPitch(auto, Double.POSITIVE_INFINITY, 90));
        assertEquals(0, InBedCamera.firstPersonPitch(VANILLA, 3, 90), 0);
    }

    @Test void cinematicRisesFiftyDegreesOverFourSecondsWhateverTheTilt() {
        assertEquals(0, InBedCamera.firstPersonPitch(CINEMATIC, 0, 90), 0);
        assertEquals(-25, InBedCamera.firstPersonPitch(CINEMATIC.withTilt(0), 2, 90), 1e-4);
        assertEquals(-50, InBedCamera.firstPersonPitch(CINEMATIC, 4, 90));
        assertEquals(-50, InBedCamera.firstPersonPitch(CINEMATIC, Double.POSITIVE_INFINITY, 110));
    }

    @Test void theEyeMovesAlongTheBedTowardTheFeetOrStraightUp() {
        // Vanilla's bed view faces the foot of the bed; yaw 0 faces south (+z).
        var feet = InBedCamera.eyeOffset(Settings.Eye.TOWARD_FEET, 0);
        assertEquals(0, feet.x, 1e-6);
        assertEquals(0, feet.y, 1e-6);
        assertEquals(.4, feet.z, 1e-6);
        var west = InBedCamera.eyeOffset(Settings.Eye.TOWARD_FEET, 90);
        assertEquals(-.4, west.x, 1e-6);
        assertEquals(.3, InBedCamera.eyeOffset(Settings.Eye.RAISED, 45).y, 1e-6);
        assertEquals(0, InBedCamera.eyeOffset(Settings.Eye.VANILLA, 45).lengthSqr());
        assertEquals(0, InBedCamera.eyeOffset(Settings.Eye.VANILLA_POSE, 45).lengthSqr());
    }
}
