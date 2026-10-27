package dev.sleeptrails.vitrail;

import dev.sleeptrails.Observation;
import dev.sleeptrails.shader.StarSource;
import dev.vitrail.pack.source.ShaderPackSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** In-memory adapter for one content-identified pack. Never writes the pack or distributes its source. */
public final class StarSourcePatch {
    public static final String PACK_SHA = StarSource.PACK_SHA;
    public static final String STARS_SHA = StarSource.STARS_SHA;
    private static final Map<ShaderPackSource, Map<String, List<String>>> SOURCES = Collections.synchronizedMap(new WeakHashMap<>());
    private StarSourcePatch() {}

    public static String sha(byte[] input) { return StarSource.sha(input); }

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

    public static String transform(String original) { return StarSource.transform(original); }
}
