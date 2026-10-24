package dev.sleeptrails;

import java.util.Arrays;

/**
 * The band each of vanilla's stars sweeps behind it while the sky turns (dev.9). Vanilla turns its stars about the buffer's
 * X axis, which carries every point along its own circle of constant polar angle, so in (turn angle, polar angle) a star's
 * quad only slides. What it covers over an arc is the quad plus the band its back edges sweep, the edges that face where it
 * came from. The band meets the quad only along those edges, so drawn beside vanilla's own quad it adds each star's light to
 * a pixel once. No Minecraft imports: this is the unit-test seam.
 */
public final class StarTrailMesh {
    /** Floats per vertex: the corner's position, how far back along the arc it lies (0 at the star, 1 at the trail's end), 0. */
    public static final int STRIDE = 5;
    /** Steps along the arc. At 16 a step of the longest trail (about 18°) bends under a twentieth of a pixel off its chord at 1080p and FOV 70. */
    public static final int STEPS = 16;
    private StarTrailMesh() {}

    /** {@code corners} holds vanilla's star buffer: x, y, z per vertex, four vertices per star, in drawing order. */
    public static float[] build(float[] corners, int steps) {
        if (corners.length % 12 != 0 || steps < 1) throw new IllegalArgumentException("four xyz corners per star, one step or more");
        // A convex quad faces back with at most three edges.
        float[] out = new float[corners.length / 12 * 3 * steps * 4 * STRIDE];
        int size = 0;
        double[] turn = new double[4], polar = new double[4];
        for (int star = 0; star < corners.length; star += 12) {
            double y = 0, z = 0;
            for (int v = 0; v < 4; v++) { y += corners[star + 3 * v + 1]; z += corners[star + 3 * v + 2]; }
            double center = Math.atan2(z, y);
            double area = 0;
            for (int v = 0; v < 4; v++) {
                float cx = corners[star + 3 * v], cy = corners[star + 3 * v + 1], cz = corners[star + 3 * v + 2];
                turn[v] = Math.IEEEremainder(Math.atan2(cz, cy) - center, 2 * Math.PI);
                polar[v] = Math.atan2(Math.hypot(cy, cz), cx);
            }
            for (int v = 0; v < 4; v++) area += turn[v] * polar[(v + 1) & 3] - turn[(v + 1) & 3] * polar[v];
            if (area == 0) continue;
            for (int v = 0; v < 4; v++) {
                int next = (v + 1) & 3;
                double rise = polar[next] - polar[v];
                // The outward side of an edge faces back (toward a smaller turn angle) when it runs down a counterclockwise
                // quad, or up a clockwise one. An edge of constant polar angle sweeps no area.
                if (area > 0 ? rise >= 0 : rise <= 0) continue;
                for (int step = 0; step < steps; step++) {
                    float near = step / (float)steps, far = (step + 1) / (float)steps;
                    size = corner(out, size, corners, star + 3 * v, near);
                    size = corner(out, size, corners, star + 3 * next, near);
                    size = corner(out, size, corners, star + 3 * next, far);
                    size = corner(out, size, corners, star + 3 * v, far);
                }
            }
        }
        return Arrays.copyOf(out, size);
    }

    private static int corner(float[] out, int size, float[] corners, int at, float back) {
        out[size] = corners[at];
        out[size + 1] = corners[at + 1];
        out[size + 2] = corners[at + 2];
        out[size + 3] = back;
        out[size + 4] = 0;
        return size + STRIDE;
    }
}
