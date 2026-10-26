package dev.sleeptrails;

/** Owns the sleep/time/frame protocol without Minecraft, global settings, rendering or I/O. */
public final class SkyTransition {
    public record Options(boolean effect, boolean timeJumps, boolean trails) {}
    public enum StartKind { PREVIEW, CONFIRMED, AWAKE }
    public record Start(StartKind kind, double visible, double finishFrom) {}
    public record Snapshot(PresentationClock.Frame sky, StarExposure.Sample exposure, double cloudOffset,
                           double previewSeconds, boolean shiftedEpoch, boolean confirmed, boolean returning,
                           boolean awake) {
        public long shaderTime(long authoritative) {
            return sky.active() || shiftedEpoch ? (long)Math.floor(sky.time()) : authoritative;
        }
    }

    private final PresentationClock clock = new PresentationClock(5_000_000_000L, 250_000_000L, 1_500_000_000L);
    private final CloudClock clouds = new CloudClock();
    private final StarExposure exposure = new StarExposure();
    private Object world;
    private boolean effect = true, timeJumps = true, trails = true;
    private Snapshot snapshot = capture(new PresentationClock.Frame(false, 0, 1, 0), Double.NaN);

    /** Observe the current context and local sleep before processing a packet or rendering a frame. */
    public Start observe(Object identity, boolean supported, boolean sleeping, double authoritative, long now,
                         Options options) {
        if (!clock.active()) {
            effect = options.effect();
            timeJumps = options.timeJumps();
        }
        trails = options.trails();
        boolean enabled = effect && supported;
        clock.context(identity, enabled);
        if (world != identity) {
            clouds.reset();
            world = identity;
        }
        if (!clock.observeSleep(enabled && sleeping, authoritative, now)) return null;
        var shown = clock.frame(now, authoritative);
        exposure.begin(shown.motionTime(), trails);
        return new Start(StartKind.PREVIEW, shown.time(), clock.finishFrom());
    }

    /** Called before the authoritative update. Sampling here must not publish the next render frame. */
    public Start timeUpdate(double before, double after, float rate, long now) {
        long generation = clock.frame(now, before).generation();
        boolean started = clock.update(before, after, rate, now, timeJumps);
        var shown = clock.frame(now, after);
        if (shown.generation() != generation) {
            exposure.reset();
            snapshot = capture(snapshot.sky(), snapshot.previewSeconds());
        }
        if (!started) return null;
        if (clock.awake()) exposure.begin(shown.motionTime(), trails);
        return new Start(clock.awake() ? StartKind.AWAKE : StartKind.CONFIRMED, shown.time(), clock.finishFrom());
    }

    /** Publish the sky and all derived render inputs together, including the final cloud-motion segment. */
    public Snapshot beginFrame(long now, double authoritative, double gameTime) {
        var sky = clock.frame(now, authoritative);
        clouds.update(gameTime, sky);
        exposure.update(sky);
        snapshot = capture(sky, clock.previewSeconds(now));
        return snapshot;
    }

    public Snapshot snapshot() { return snapshot; }

    /** Reload/resize cancellation keeps cloud position and requires a new bed entry before another local preview. */
    public void cancel() {
        clock.cancel();
        exposure.reset();
        snapshot = capture(new PresentationClock.Frame(false, 0, 1, snapshot.sky().generation() + 1), Double.NaN);
    }

    private Snapshot capture(PresentationClock.Frame sky, double previewSeconds) {
        return new Snapshot(sky, exposure.sample(), clouds.offset(), previewSeconds, clock.shiftedEpoch(),
            clock.confirmed(), clock.returning(), clock.awake());
    }
}
