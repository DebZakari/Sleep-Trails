package dev.sleeptrails;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The in-bed camera, placed by InBedCameraMixin after vanilla has placed its own. It runs only while the local player sleeps
 * with the transition running, and is render-only: the player never turns.
 * <p>
 * First person, both modes: the eye moves as {@link Settings.Eye} says, with vanilla's own cut to the bed view, so it never
 * slides. Vanilla mode then tilts the view up by its setting, eased in over a second; Cinematic rises 50° over the preview's
 * first 4 s. Third person: Vanilla mode keeps vanilla's camera. Cinematic orbits the sleeper to a three-quarter shot toward the
 * celestial pole (prototyped in cinematic-02), and eases back to vanilla's camera after waking.
 */
public final class InBedCamera {
    static final double TILT_IN = 1, MOVE = 4, EASE_BACK = 1.2;
    static final float RISE = 50;
    /** The eye moves, in blocks (bedoptions-01). */
    static final double FEET = .4, RAISE = .3;
    /** The final shot looks this far off the pole axis, so the sleeper shows at three-quarters rather than behind the pillow. */
    static final double OFFSET = 30;
    /** The orbit starts this far round from the final shot, on the foot side of the bed. */
    static final double SWEEP = 100;
    static final double NEAR = 2, FAR = 3.4, HIGH = 1.2, LOW = .7;
    /** Where the sleeper settles in the frame: this fraction of the way from the center to the bottom edge. */
    static final double SUBJECT = .6;
    public record Pose(Vec3 position, float yRot, float xRot) {}
    /** {@code look} aims at the pivot from {@code reach} away; {@code pitch} is the shot's final tilt. */
    public record Orbit(Vec3 pivot, float yaw, float look, float pitch, double reach) {}
    private static Pose last;
    private static long stopNanos;
    private static boolean running, vanillaPose;
    private InBedCamera() {}

    /** This frame's seconds into the in-bed camera, or NaN while it is off. Tracks when it stops, for the ease-back. */
    public static double seconds(boolean sleeping, boolean detached, long now) {
        double seconds = sleeping ? Presentation.previewSeconds() : Double.NaN;
        boolean nowRunning = !Double.isNaN(seconds);
        if (running && !nowRunning) stopNanos = now;
        running = nowRunning;
        vanillaPose = nowRunning && !detached && Settings.get().eye() == Settings.Eye.VANILLA_POSE;
        if (vanillaPose) EmfBodyPose.install();
        return seconds;
    }

    /** Whether the local player's own body is drawn in the vanilla pose this frame (EmfBodyPose). */
    public static boolean vanillaPose() { return vanillaPose; }

    /** The Vanilla mode's tilt in degrees. Auto is half the vertical field of view less 10°, so the frame's bottom edge sits 10° below the horizon. */
    public static int tilt(int setting, int fov) {
        return setting >= 0 ? setting : Math.clamp(fov / 2 - 10, 0, Settings.MAX_TILT);
    }

    /** The first-person pitch, negative being up. */
    public static float firstPersonPitch(Settings.Values settings, double seconds, int fov) {
        if (settings.camera() == Settings.Camera.CINEMATIC) return (float)(-RISE * smootherstep(seconds / MOVE));
        return (float)(-tilt(settings.tilt(), fov) * smootherstep(seconds / TILT_IN));
    }

    /** How far the first-person eye moves from vanilla's. Vanilla's bed view faces the foot of the bed, so its heading is the way to the feet. */
    public static Vec3 eyeOffset(Settings.Eye eye, float yRot) {
        return switch (eye) {
            case TOWARD_FEET -> Vec3.directionFromRotation(0, yRot).scale(FEET);
            case RAISED -> new Vec3(0, RAISE, 0);
            case VANILLA, VANILLA_POSE -> Vec3.ZERO;
        };
    }

    /** The orbit's camera for a sleeper at {@code body}, before collision. The center of the bed is the pivot. */
    public static Orbit orbit(Vec3 body, Direction bed, double seconds, int fov) {
        // The in-bed view looks toward the foot. Minecraft yaw 0 faces south (+z), 180 north.
        double footX = -bed.getStepX(), footZ = -bed.getStepZ();
        var pivot = body.add(footX * .45, .2, footZ * .45);
        // The sky turns about the north-south axis, so the trails circle a pole on the north or south horizon.
        float poleYaw = footZ < 0 ? 180 : 0;
        double e = smootherstep(seconds / MOVE);
        float yaw = (float)(poleYaw + OFFSET + SWEEP * (1 - e));
        double distance = Mth.lerp(e, NEAR, FAR), height = Mth.lerp(e, HIGH, LOW);
        float look = (float)Math.toDegrees(Math.atan2(height, distance));
        // The tilt up trails the orbit, so the sky is revealed as the camera settles.
        double composed = Math.toDegrees(Math.atan(SUBJECT * Math.tan(Math.toRadians(fov / 2.0)))) - Math.toDegrees(Math.atan2(LOW, FAR));
        double w = smootherstep((seconds / MOVE - .2) / .8);
        return new Orbit(pivot, yaw, look, (float)Mth.lerp(w, look, -composed), Math.hypot(distance, height));
    }

    public static void remember(Pose pose) { last = pose; }
    public static void forget() { last = null; }

    /** After the orbit stops, blends from its last shot to vanilla's camera, or null when there is nothing to blend. */
    public static Pose easeBack(Pose vanilla, long now) {
        if (last == null) return null;
        double u = (now - stopNanos) / 1e9 / EASE_BACK;
        if (u >= 1) { last = null; return null; }
        double s = smootherstep(u);
        return new Pose(last.position().lerp(vanilla.position(), s), last.yRot() + Mth.wrapDegrees(vanilla.yRot() - last.yRot()) * (float)s,
            (float)Mth.lerp(s, last.xRot(), vanilla.xRot()));
    }

    static double smootherstep(double p) {
        p = Math.clamp(p, 0, 1);
        return p * p * p * (p * (p * 6 - 15) + 10);
    }
}
