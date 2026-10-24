package dev.sleeptrails;

/**
 * Cloud animation uses elapsed game time, which does not skip when a night ends. Integrate the sky's visual elapsed
 * time during a sleep, then retain the extra drift so returning to ordinary cloud speed never restores the old position.
 */
public final class CloudClock {
    private boolean sampled;
    private boolean active;
    private long generation;
    private double gameTime;
    private double skyTime;
    private double offset;

    public void update(double gameTime, PresentationClock.Frame frame) {
        if (sampled && active && generation == frame.generation()) {
            offset += (frame.motionTime() - skyTime) - (gameTime - this.gameTime);
        }
        sampled = true;
        active = frame.active();
        generation = frame.generation();
        this.gameTime = gameTime;
        skyTime = frame.motionTime();
    }

    public double offset() { return offset; }

    /** A different world starts at its own vanilla cloud position. Lifecycle cancellation keeps the current position. */
    public void reset() {
        sampled = active = false;
        offset = 0;
    }
}
