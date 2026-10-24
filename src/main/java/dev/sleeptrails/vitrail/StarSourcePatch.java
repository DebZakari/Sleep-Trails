package dev.sleeptrails.vitrail;

import dev.sleeptrails.Observation;
import dev.vitrail.pack.source.ShaderPackSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** In-memory adapter for one content-identified pack. Never writes the pack or distributes its source. */
public final class StarSourcePatch {
    public static final String PACK_SHA = "98ac741dee294d2a3c82d153349148c3bb7fb688c30eb3b49c3b9ac37e968c5a";
    public static final String STARS_SHA = "4d3b66e7af610e9ee09e7cc0c562e534c7a804941151bef3ed04737478bdfd02";
    private static final Map<ShaderPackSource, Map<String, List<String>>> SOURCES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final String FUNCTION = "vec3 GetStars(vec2 starCoord, float VdotU, float VdotS)";
    private static final String CODE = code();
    private StarSourcePatch() {}

    private static String code() {
        try (var input = StarSourcePatch.class.getResourceAsStream("/assets/sleep_trails/star_exposure.glsl")) {
            if (input == null) throw new IOException("Missing exposure source");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    public static String sha(byte[] input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    public static void opened(ShaderPackSource source, Path archive) {
        if (Boolean.getBoolean("sleeptrails.adapter.disabled")) return;
        try {
            if (Files.isRegularFile(archive) && sha(Files.readAllBytes(archive)).equals(PACK_SHA)) {
                SOURCES.put(source, new HashMap<>());
                LoggerFactory.getLogger("sleep_trails").info("Content-identified star adapter enabled for {} (in memory only)", source.packName());
            } else LoggerFactory.getLogger("sleep_trails").info("Star adapter unavailable for {}: unsupported pack identity; clock-only rendering", source.packName());
        } catch (IOException error) {
            LoggerFactory.getLogger("sleep_trails").warn("Cannot identify star pack; using clock-only rendering", error);
        }
    }

    public static List<String> read(ShaderPackSource source, Path file, List<String> original) {
        var cache = SOURCES.get(source);
        if (cache == null) return original;
        String relative = source.rel(file);
        if (!relative.equals("lib/atmospherics/stars.glsl")) return original;
        synchronized (cache) {
            return cache.computeIfAbsent(relative, key -> {
                try {
                    if (!sha(Files.readAllBytes(file)).equals(STARS_SHA)) return original;
                    String patched = transform(String.join("\n", original));
                    Observation.event("star_adapter", Map.of("member", relative, "originalSha256", STARS_SHA,
                        "patchedSha256", sha(patched.getBytes(StandardCharsets.UTF_8)),
                        "reference", Boolean.getBoolean("sleeptrails.reference")));
                    return List.of(patched.split("\n", -1));
                } catch (IOException error) { throw new IllegalStateException("Cannot read identified star source", error); }
            });
        }
    }

    public static String transform(String original) {
        if (original.indexOf(FUNCTION) < 0 || original.indexOf(FUNCTION) != original.lastIndexOf(FUNCTION))
            throw new IllegalArgumentException("Unexpected original star function");
        String prefix = Boolean.getBoolean("sleeptrails.reference") ? "\n#define SLEEPTRAILS_REFERENCE\n" : "\n";
        return original.replace(FUNCTION, "vec3 SleepTrailsInstantStars(vec2 starCoord, float VdotU, float VdotS)") + prefix + CODE;
    }
}
