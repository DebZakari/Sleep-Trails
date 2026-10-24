package dev.sleeptrails;

/** Pure event-in/frame-out controller. It owns no game clock, entity or network connection. */
public final class PresentationClock {
    public record Frame(boolean active, double time, double progress, long generation, double motionTime) {
        public Frame(boolean active, double time, double progress, long generation) {
            this(active, time, progress, generation, time);
        }
    }
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
    private boolean awake;
    /** Whole visual days retained until midday masks an authoritative day-number reset. */
    private double epochOffset;
    /** Continuous elapsed sky travel for clouds/exposure, independent of day-number reconciliation. */
    private double motionOffset;
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
            double lead = frame(now, authoritative).time() - (authoritative + epochOffset);
            armed = true; woke = false;
            if (phase(authoritative) >= EVENING) {
                awake = false;
                previewNanos = now;
                previewLead = lead;
                double displayed = authoritative + epochOffset;
                previewTarget = Math.max(displayed, Math.floor(displayed / 24000.0 + 1) * 24000.0 - 500);
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
    public boolean update(double before, double after, float rate, long now, boolean smoothTimeJumps) {
        boolean localSleep = sleeping || armed || (active && !awake);
        if (!awake && update(before, after, rate, now)) return true;
        if (enabled && smoothTimeJumps && !localSleep && rate == 1.0F && qualifies(before, after)) {
            var shown = frame(now, before);
            offset = phase(after - shown.time());
            epochOffset = shown.time() + offset - after;
            startNanos = now;
            active = awake = true;
            confirmed = returning = false;
            return true;
        }
        if (awake && (Math.abs(after - before) > 20 || rate != 1.0F)) cancel();
        return false;
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
        offset = Math.max(0, after + epochOffset - shown.time());
        // The trails' end fade carries on from an ease-back instead of growing back.
        finishFrom = Math.max(.75, shown.progress());
        active = true;
        confirmed = true;
        armed = returning = false;
        return true;
    }

    public Frame frame(long now, double authoritative) {
        authoritative += epochOffset;
        if (!active) return presented(false, authoritative, 1);
        if (awake) {
            double p = Math.clamp((double)(now - startNanos) / durationNanos, 0, 1);
            if (p >= 1) active = awake = false;
            return presented(active, authoritative - offset * (1 - smooth(p)), p);
        }
        if (!confirmed && woke && now - wakeNanos > wakeWindowNanos) abort();
        if (returning) {
            double p = Math.clamp((double)(now - returnNanos) / Math.max(1, durationNanos * 3 / 10), 0, 1);
            if (p >= 1) { active = returning = false; return presented(false, authoritative, 1); }
            // The preview stays frozen at the abort instant and eases out; the end fade covers the whole return.
            double lead = preview(returnNanos, authoritative) - authoritative;
            return presented(true, authoritative + lead * (1 - smooth(p)), .8 + .2 * p);
        }
        if (!confirmed) {
            double p = Math.clamp((double)(now - previewNanos) / durationNanos, 0, 1);
            return presented(true, preview(now, authoritative), p * .75);
        }
        // Confirmation continues from the last visible night, rather than exposing the server's morning jump.
        long finishNanos = Math.max(1, durationNanos * 2 / 5);
        double p = Math.clamp((double)(now - startNanos) / finishNanos, 0, 1);
        if (p >= 1) { active = confirmed = false; return presented(false, authoritative, 1); }
        return presented(true, authoritative - offset * (1 - smooth(p)), finishFrom + (1 - finishFrom) * p);
    }

    private Frame presented(boolean active, double time, double progress) {
        // Vanilla has no stars here and the supported pack strongly attenuates them. Dawn/dusk are deliberately excluded:
        // their stars are still visible. Cloud/exposure travel does not include this whole-day rebase.
        if (epochOffset != 0 && phase(time) >= 4000 && phase(time) <= 8000) {
            time -= epochOffset;
            previewTarget -= epochOffset;
            motionOffset += epochOffset;
            epochOffset = 0;
        }
        return new Frame(active, time, progress, generation, time + motionOffset);
    }

    /** Seconds into the preview while it runs, infinite once the skip is confirmed, and NaN when inactive or easing back. */
    public double previewSeconds(long now) {
        if (!active || returning || awake) return Double.NaN;
        return confirmed ? Double.POSITIVE_INFINITY : (now - previewNanos) / 1e9;
    }

    public boolean confirmed() { return confirmed; }
    /** The progress the confirmed finish started from: 0.75, or more when it took over an ease-back. */
    public double finishFrom() { return finishFrom; }
    public boolean active() { return active; }
    public boolean returning() { return returning; }
    public boolean awake() { return awake; }
    public boolean shiftedEpoch() { return epochOffset != 0; }

    /** Cancel this presentation without interpreting a still-sleeping player as a new bed entry. */
    public void cancel() {
        boolean stillSleeping = sleeping;
        reset();
        sleeping = stillSleeping;
    }

    public void reset() {
        active = confirmed = returning = armed = sleeping = woke = awake = false;
        offset = previewLead = 0;
        epochOffset = motionOffset = 0;
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
    private static boolean qualifies(double before, double after) {
        return Double.isFinite(before) && Double.isFinite(after) && Math.abs(after - before) > 20
            && Math.abs(Math.IEEEremainder(after - before, 24000)) > 20
            && (phase(before) < EVENING) != (phase(after) < EVENING);
    }
}
