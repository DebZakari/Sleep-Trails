package dev.sleeptrails.shader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Shared, renderer-independent transform for the one supported shader pack. */
public final class StarSource {
    public static final String PACK_SHA = "98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a";
    public static final String STARS_SHA = "4d3b66e7af610e9ee09e7cc0c562e534c7a804941151bef3ed04737478bdfd02";
    public static final String MEMBER = "lib/atmospherics/stars.glsl";
    private static final String FUNCTION = "vec3 GetStars(vec2 starCoord, float VdotU, float VdotS)";
    private static final String CODE = code();
    private StarSource() {}

    private static String code() {
        try (var input = StarSource.class.getResourceAsStream("/assets/sleep_trails/star_exposure.glsl")) {
            if (input == null) throw new IOException("Missing exposure source");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    public static String sha(byte[] input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    public static String transform(String original) {
        if (original.indexOf(FUNCTION) < 0 || original.indexOf(FUNCTION) != original.lastIndexOf(FUNCTION))
            throw new IllegalArgumentException("Unexpected original star function");
        String prefix = Boolean.getBoolean("sleeptrails.reference") ? "\n#define SLEEPTRAILS_REFERENCE\n" : "\n";
        return original.replace(FUNCTION, "vec3 SleepTrailsInstantStars(vec2 starCoord, float VdotU, float VdotS)") + prefix + CODE;
    }
}
