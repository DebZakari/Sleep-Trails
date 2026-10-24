package dev.sleeptrails;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class StarTrailMeshTest {
    /** Tangent quads on vanilla's sphere of radius 100, sized and rolled as vanilla's are, either winding. */
    private static float[] stars(long seed, int count, boolean reversed) {
        Random random = new Random(seed);
        float[] out = new float[count * 12];
        for (int star = 0; star < count; star++) {
            double nx, ny, nz, length;
            do {
                nx = random.nextDouble() * 2 - 1; ny = random.nextDouble() * 2 - 1; nz = random.nextDouble() * 2 - 1;
                length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            } while (length < .1 || length >= 1 || Math.hypot(ny, nz) / length < Math.sin(Math.toRadians(5)));
            nx /= length; ny /= length; nz /= length;
            double size = .15 + random.nextDouble() * .1, roll = random.nextDouble() * 2 * Math.PI;
            // u is perpendicular to the centre and to Y; w completes the tangent basis.
            double ux = nz, uy = 0, uz = -nx, ul = Math.hypot(ux, uz);
            if (ul < 1e-6) { ux = 1; uz = 0; ul = 1; }
            ux /= ul; uz /= ul;
            double wx = ny * uz - nz * uy, wy = nz * ux - nx * uz, wz = nx * uy - ny * ux;
            double[][] local = {{size, -size}, {size, size}, {-size, size}, {-size, -size}};
            for (int v = 0; v < 4; v++) {
                double[] corner = local[reversed ? 3 - v : v];
                double a = corner[0] * Math.cos(roll) - corner[1] * Math.sin(roll);
                double b = corner[0] * Math.sin(roll) + corner[1] * Math.cos(roll);
                out[star * 12 + 3 * v] = (float)(100 * nx + a * ux + b * wx);
                out[star * 12 + 3 * v + 1] = (float)(100 * ny + a * uy + b * wy);
                out[star * 12 + 3 * v + 2] = (float)(100 * nz + a * uz + b * wz);
            }
        }
        return out;
    }

    /** A corner in (turn angle, polar angle), turned back by {@code back} of the arc. */
    private static double[] chart(float x, float y, float z, double center, double back) {
        double turn = Math.IEEEremainder(Math.atan2(z, y) - center, 2 * Math.PI) - back;
        return new double[] {turn, Math.atan2(Math.hypot(y, z), x)};
    }

    private static boolean inside(double[][] polygon, double px, double py) {
        int sign = 0;
        for (int v = 0; v < polygon.length; v++) {
            double[] a = polygon[v], b = polygon[(v + 1) % polygon.length];
            double cross = (b[0] - a[0]) * (py - a[1]) - (b[1] - a[1]) * (px - a[0]);
            int s = (int)Math.signum(cross);
            if (s == 0) continue;
            if (sign != 0 && s != sign) return false;
            sign = s;
        }
        return true;
    }

    @Test void everyVertexIsAVanillaCornerBitForBitOnAStepBoundary() {
        float[] corners = stars(1, 200, false);
        float[] band = StarTrailMesh.build(corners, 16);
        assertEquals(0, band.length % (4 * StarTrailMesh.STRIDE));
        for (int quad = 0; quad < band.length; quad += 4 * StarTrailMesh.STRIDE) {
            int owner = -1;
            for (int v = 0; v < 4; v++) {
                int at = quad + v * StarTrailMesh.STRIDE;
                int found = -1;
                for (int c = 0; c < corners.length && found < 0; c += 3)
                    if (Float.floatToRawIntBits(corners[c]) == Float.floatToRawIntBits(band[at])
                        && Float.floatToRawIntBits(corners[c + 1]) == Float.floatToRawIntBits(band[at + 1])
                        && Float.floatToRawIntBits(corners[c + 2]) == Float.floatToRawIntBits(band[at + 2])) found = c / 12;
                assertTrue(found >= 0, "a band vertex that is not a star corner");
                if (owner < 0) owner = found;
                assertEquals(owner, found, "a band quad spanning two stars");
                float back = band[at + 3];
                assertEquals(Math.round(back * 16) / 16f, back, 0f);
                assertEquals(0, band[at + 4]);
            }
            assertEquals(band[quad + 3], band[quad + StarTrailMesh.STRIDE + 3]);
            assertEquals(band[quad + 2 * StarTrailMesh.STRIDE + 3], band[quad + 3 * StarTrailMesh.STRIDE + 3]);
            assertEquals(1 / 16f, band[quad + 2 * StarTrailMesh.STRIDE + 3] - band[quad + 3], 1e-7);
        }
    }

    @Test void eachStarSweepsOneToThreeEdgesAcrossTheWholeArc() {
        for (boolean reversed : new boolean[] {false, true}) {
            float[] corners = stars(2, 1, reversed);
            for (int star = 0; star < 300; star++) {
                float[] one = stars(1000 + star, 1, reversed);
                int edges = StarTrailMesh.build(one, 16).length / (4 * StarTrailMesh.STRIDE) / 16;
                assertTrue(edges >= 1 && edges <= 3, edges + " back edges");
            }
            assertEquals(StarTrailMesh.build(corners, 1).length * 8, StarTrailMesh.build(corners, 8).length);
        }
    }

    @Test void theBandAndTheStarCoverItsSweepOnceWithNoOverlap() {
        Random random = new Random(3);
        for (boolean reversed : new boolean[] {false, true}) {
            for (int star = 0; star < 120; star++) {
                float[] one = stars(5000 + star, 1, reversed);
                double arc = .02 + random.nextDouble() * .3;
                double y = 0, z = 0;
                for (int v = 0; v < 4; v++) { y += one[3 * v + 1]; z += one[3 * v + 2]; }
                double center = Math.atan2(z, y);
                double[][] quad = new double[4][];
                for (int v = 0; v < 4; v++) quad[v] = chart(one[3 * v], one[3 * v + 1], one[3 * v + 2], center, 0);
                float[] band = StarTrailMesh.build(one, 4);
                int quads = band.length / (4 * StarTrailMesh.STRIDE);
                double[][][] pieces = new double[quads][4][];
                for (int q = 0; q < quads; q++)
                    for (int v = 0; v < 4; v++) {
                        int at = (q * 4 + v) * StarTrailMesh.STRIDE;
                        pieces[q][v] = chart(band[at], band[at + 1], band[at + 2], center, band[at + 3] * arc);
                    }
                double minTurn = Double.MAX_VALUE, maxTurn = -Double.MAX_VALUE, minPolar = Double.MAX_VALUE, maxPolar = -Double.MAX_VALUE;
                for (double[] corner : quad) {
                    minTurn = Math.min(minTurn, corner[0]); maxTurn = Math.max(maxTurn, corner[0]);
                    minPolar = Math.min(minPolar, corner[1]); maxPolar = Math.max(maxPolar, corner[1]);
                }
                double pad = (maxPolar - minPolar) * .3;
                for (int sample = 0; sample < 4000; sample++) {
                    double px = minTurn - arc - pad + random.nextDouble() * (maxTurn - minTurn + arc + 2 * pad);
                    double py = minPolar - pad + random.nextDouble() * (maxPolar - minPolar + 2 * pad);
                    int covered = inside(quad, px, py) ? 1 : 0;
                    for (double[][] piece : pieces) if (inside(piece, px, py)) covered++;
                    assertEquals(swept(quad, px, py, arc) ? 1 : 0, covered, "star " + star + " at (" + px + ", " + py + ")");
                }
            }
        }
    }

    /** In the sweep when the star passed over the point at some moment of the arc: the quad meets the run ahead of it. */
    private static boolean swept(double[][] quad, double px, double py, double arc) {
        double from = Double.MAX_VALUE, to = -Double.MAX_VALUE;
        for (int v = 0; v < 4; v++) {
            double[] a = quad[v], b = quad[(v + 1) % 4];
            if ((a[1] - py) * (b[1] - py) > 0 || a[1] == b[1]) continue;
            double x = a[0] + (py - a[1]) / (b[1] - a[1]) * (b[0] - a[0]);
            from = Math.min(from, x);
            to = Math.max(to, x);
        }
        return from <= to && from <= px + arc && to >= px;
    }

    @Test void aStarWithNoAreaHasNoBandAndMalformedInputIsRefused() {
        float[] flat = {0, 100, 0, 0, 100, 0, 0, 100, 0, 0, 100, 0};
        assertEquals(0, StarTrailMesh.build(flat, 16).length);
        assertThrows(IllegalArgumentException.class, () -> StarTrailMesh.build(new float[11], 16));
        assertThrows(IllegalArgumentException.class, () -> StarTrailMesh.build(new float[12], 0));
    }
}
