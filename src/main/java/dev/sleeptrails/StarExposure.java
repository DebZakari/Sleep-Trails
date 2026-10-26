package dev.sleeptrails;

/** Frame-owned presentation exposure. No image history, shader clocks or authoritative state are advanced here. */
public final class StarExposure {
    public record Sample(float ticks, int clockMod, long generation) {}
    private boolean trails = true;
    public static final float MAX_TICKS = 1024;
    private double start;
    private Sample sample = new Sample(0, 0, 0);
    StarExposure() {}

    /** A re-entry during an ease-back keeps the current trail width instead of collapsing it. */
    void begin(double motionTime, boolean trails) { start = motionTime - sample.ticks(); this.trails = trails; }
    void update(PresentationClock.Frame frame) {
        float ticks = width(frame.active() && trails, start, frame.motionTime(), frame.progress());
        sample = new Sample(ticks, (int)Math.floorMod((long)Math.floor(frame.time()), 2_400_000L), frame.generation());
    }
    public static float width(boolean active, double start, double time, double progress) {
        if (!active) return 0;
        double endFade = Math.clamp((1 - progress) / 0.2, 0, 1);
        endFade = endFade * endFade * (3 - 2 * endFade);
        return (float)(Math.clamp(time - start, 0, MAX_TICKS) * endFade);
    }
    /**
     * The arc vanilla's stars turned through over an exposure, in radians, from its star angle (degrees) now and at the
     * exposure's start. A timeline that ran backwards gives no trail.
     */
    public static float arc(float now, float start) {
        double degrees = Math.IEEEremainder(now - start, 360);
        return degrees > 0 ? (float)Math.toRadians(degrees) : 0;
    }
    Sample sample() { return sample; }
    void reset() { sample = new Sample(0, 0, sample.generation() + 1); }
}
