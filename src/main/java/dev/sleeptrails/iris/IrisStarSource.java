package dev.sleeptrails.iris;

import dev.sleeptrails.Observation;
import dev.sleeptrails.shader.StarSource;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Permission exists only during one ShaderPack construction and only for its exact archive and root. */
public final class IrisStarSource {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private IrisStarSource() {}

    public static Scope open(Path root) {
        root = root.toAbsolutePath().normalize();
        Path admitted = null;
        try {
            URI uri = root.toUri();
            String part = uri.getRawSchemeSpecificPart();
            int separator = part.indexOf("!/");
            if (!Boolean.getBoolean("sleeptrails.adapter.disabled") && "jar".equals(uri.getScheme()) && separator > 0) {
                Path archive = Path.of(URI.create(part.substring(0, separator)));
                if (Files.isRegularFile(archive) && StarSource.sha(Files.readAllBytes(archive)).equals(StarSource.PACK_SHA)
                        && root.normalize().toString().equals("/shaders")) admitted = root.normalize();
            }
        } catch (Exception error) {
            LoggerFactory.getLogger("sleep_trails").warn("Cannot identify Iris star pack; using clock-only rendering", error);
        }
        var scope = new Scope(admitted, CURRENT.get());
        CURRENT.set(scope);
        LoggerFactory.getLogger("sleep_trails").info("Iris star adapter: {}", admitted == null ? "clock-only (pack not admitted)" : "content-identified pack, in memory only");
        return scope;
    }

    public static String read(Path file, String original) {
        Scope scope = CURRENT.get();
        if (scope == null || scope.root == null || !scope.root.resolve(StarSource.MEMBER).equals(file.toAbsolutePath().normalize())) return original;
        if (!StarSource.sha(original.getBytes(StandardCharsets.UTF_8)).equals(StarSource.STARS_SHA)) return original;
        String patched = transform(original);
        Observation.event("star_adapter", Map.of("renderer", "iris", "member", StarSource.MEMBER,
            "originalSha256", StarSource.STARS_SHA, "patchedSha256", StarSource.sha(patched.getBytes(StandardCharsets.UTF_8)),
            "reference", Boolean.getBoolean("sleeptrails.reference")));
        return patched;
    }

    private static String transform(String original) {
        String text = StarSource.transform(original);
        if (Boolean.getBoolean("sleeptrails.reference")) return text;
        // Each remaining noise factor is at most one. Once below the final threshold, this star stays zero.
        String guard = """
            #if NIGHT_STAR_AMOUNT == 1
                if (star <= 0.82) return vec3(0.0);
            #elif NIGHT_STAR_AMOUNT == 2
                if (star <= 0.7) return vec3(0.0);
            #elif NIGHT_STAR_AMOUNT == 3
                if (star <= 0.62) return vec3(0.0);
            #elif NIGHT_STAR_AMOUNT == 4
                if (star <= 0.52) return vec3(0.0);
            #endif
            """;
        for (String needle : new String[]{"star *= GetStarNoise(starCoord.xy);", "star *= GetStarNoise(starCoord.xy+0.1);"}) {
            if (text.indexOf(needle) < 0 || text.indexOf(needle) != text.lastIndexOf(needle))
                throw new IllegalArgumentException("Unexpected Iris star noise function");
            text = text.replace(needle, needle + "\n" + guard);
        }
        return text;
    }

    public static final class Scope implements AutoCloseable {
        private final Path root;
        private final Scope previous;
        private Scope(Path root, Scope previous) { this.root = root; this.previous = previous; }
        public void close() {
            if (CURRENT.get() != this) throw new IllegalStateException("Iris pack scopes closed out of order");
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
        }
    }
}
