package dev.sleeptrails;

/** Pure event-in/frame-out controller. It owns no game clock, entity or network connection. */
public final class PresentationClock {
    public record Frame(boolean active, double time, double progress, long generation) {}
    /** Sunset. In 26.3 rain allows sleep from about 12010 and clear skies from about 12542. */
    static final double EVENING = 12000;
    private static final long TICK_NANOS = 50_000_000L;
    private final long durationNanos;
    private final long wakeWindowNanos;
    /** dev.10: how long after the wake a skip may still arrive, during the ease-back, and finish from the visible sky. */
    private final long lateSkipNanos;
    private Object world;
    private boolean enabled;
    private boolean sleeping;
    private boolean armed;
    private long wakeNanos;
    private boolean woke;
    private long startNanos;
    private double offset;
    private double finishFrom;
    private boolean active;
    private boolean confirmed;
    private boolean returning;
    private double previewTarget;
    private double previewLead;
    private long previewNanos;
    private long returnNanos;
    private long generation;

    public PresentationClock(long durationNanos, long wakeWindowNanos, long lateSkipNanos) {
        // The late window closes while the ease-back still runs, so a late skip always has a visible sky to start from.
        if (durationNanos <= 0 || wakeWindowNanos < 0 || lateSkipNanos < wakeWindowNanos
            || lateSkipNanos >= wakeWindowNanos + durationNanos * 3 / 10) throw new IllegalArgumentException("Invalid clock durations");
        this.durationNanos = durationNanos;
        this.wakeWindowNanos = wakeWindowNanos;
        this.lateSkipNanos = lateSkipNanos;
    }

    public void context(Object identity, boolean enabled) {
        if (world != identity || this.enabled != enabled) {
            reset();
            world = identity;
            this.enabled = enabled && identity != null;
        }
    }

    public boolean observeSleep(boolean nowSleeping, double authoritative, long now) {
        if (!enabled) return false;
        boolean started = false;
        if (nowSleeping && !sleeping) {
            // A re-entry during the ease-back continues from the visible sky.
            double lead = frame(now, authoritative).time() - authoritative;
            armed = true; woke = false;
            if (phase(authoritative) >= EVENING) {
                previewNanos = now;
                previewLead = lead;
                previewTarget = Math.max(authoritative, Math.floor(authoritative / 24000.0 + 1) * 24000.0 - 500);
                active = true;
                confirmed = returning = false;
                started = true;
            }
        }
        if (!nowSleeping && sleeping) { wakeNanos = now; woke = true; }
        sleeping = nowSleeping;
        if (armed && woke && now - wakeNanos > wakeWindowNanos) abort();
        return started;
    }

    /** Called before vanilla applies the packet. Returns true only for a new catch-up. */
    public boolean update(double before, double after, float rate, long now) {
        double jump = after - before;
        boolean discontinuity = jump < -20 || jump > 20 || rate != 1.0F;
        if (armed && woke && now - wakeNanos > wakeWindowNanos) abort();
        // A server that sends the time only every 20 ticks (1.21.x through ViaFabricPlus) delivers the skip after the wake
        // allowance, so the ease-back has begun. Until the late window closes, the skip still finishes from there.
        boolean late = returning && now - wakeNanos <= lateSkipNanos;
        if (!enabled || confirmed || !(armed || late)) {
            if (active && discontinuity) cancel();
            return false;
        }
        // Only a previewed sleep is confirmed. The observed vanilla skip targets the next 24000-tick boundary, and a skip
        // sent after the wake has advanced by up to the ticks since.
        double morning = Math.floor(before / 24000.0 + 1) * 24000.0;
        double since = woke ? (double)(now - wakeNanos) / TICK_NANOS : 0;
        if (!active || rate != 1.0F || phase(before) < EVENING || jump <= 20 || jump > 12000
            || after < morning - 2 || after > morning + 2 + since) {
            if (discontinuity) cancel();
            return false;
        }
        var shown = frame(now, before);
        startNanos = now;
        offset = Math.max(0, after - shown.time());
        // The trails' end fade carries on from an ease-back instead of growing back.
        finishFrom = Math.max(.75, shown.progress());
        active = true;
        confirmed = true;
        armed = returning = false;
        return true;
    }

    public Frame frame(long now, double authoritative) {
        if (!active) return new Frame(false, authoritative, 1, generation);
        if (!confirmed && woke && now - wakeNanos > wakeWindowNanos) abort();
        if (returning) {
            double p = Math.clamp((double)(now - returnNanos) / Math.max(1, durationNanos * 3 / 10), 0, 1);
            if (p >= 1) { active = returning = false; return new Frame(false, authoritative, 1, generation); }
            // The preview stays frozen at the abort instant and eases out; the end fade covers the whole return.
            double lead = preview(returnNanos, authoritative) - authoritative;
            return new Frame(true, authoritative + lead * (1 - smooth(p)), .8 + .2 * p, generation);
        }
        if (!confirmed) {
            double p = Math.clamp((double)(now - previewNanos) / durationNanos, 0, 1);
            return new Frame(true, preview(now, authoritative), p * .75, generation);
        }
        // Confirmation continues from the last visible night, rather than exposing the server's morning jump.
        long finishNanos = Math.max(1, durationNanos * 2 / 5);
        double p = Math.clamp((double)(now - startNanos) / finishNanos, 0, 1);
        if (p >= 1) { active = confirmed = false; return new Frame(false, authoritative, 1, generation); }
        return new Frame(true, authoritative - offset * (1 - smooth(p)), finishFrom + (1 - finishFrom) * p, generation);
    }

    /** Seconds into the preview while it runs, infinite once the skip is confirmed, and NaN when inactive or easing back. */
    public double previewSeconds(long now) {
        if (!active || returning) return Double.NaN;
        return confirmed ? Double.POSITIVE_INFINITY : (now - previewNanos) / 1e9;
    }

    public boolean confirmed() { return confirmed; }
    /** The progress the confirmed finish started from: 0.75, or more when it took over an ease-back. */
    public double finishFrom() { return finishFrom; }
    public boolean active() { return active; }
    public boolean returning() { return returning; }

    /** Cancel this presentation without interpreting a still-sleeping player as a new bed entry. */
    public void cancel() {
        boolean stillSleeping = sleeping;
        reset();
        sleeping = stillSleeping;
    }

    public void reset() {
        active = confirmed = returning = armed = sleeping = woke = false;
        offset = previewLead = 0;
        generation++;
    }

    /** Leaving bed without a skip eases back from the visible sky instead of snapping to the server's night. */
    private void abort() {
        armed = false;
        if (!active || confirmed || returning) return;
        returnNanos = wakeNanos + wakeWindowNanos;
        returning = true;
    }

    private double preview(long now, double authoritative) {
        double eased = smooth(Math.clamp((double)(now - previewNanos) / durationNanos, 0, 1));
        return authoritative + previewLead * (1 - eased) + Math.max(0, previewTarget - authoritative) * eased;
    }

    private static double smooth(double p) { return p * p * (3 - 2 * p); }
    private static double phase(double time) { return time - Math.floor(time / 24000.0) * 24000.0; }
}
