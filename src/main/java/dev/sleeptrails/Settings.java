package dev.sleeptrails;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * The player's settings, kept in {@code config/sleep_trails.json} and shared by the Mod Menu screen and the Sodium page.
 * The {@code -Dsleeptrails.disabled} and {@code -Dsleeptrails.trails.disabled} switches win over the file.
 */
public final class Settings {
    public enum Camera { VANILLA, CINEMATIC }
    /** Where the first-person in-bed eye sits. Each but vanilla keeps FA+Player's lying helmet out of the sky (bedoptions-01). */
    public enum Eye { VANILLA, TOWARD_FEET, RAISED, VANILLA_POSE }
    /** A tilt below zero is Auto, which follows the field of view (InBedCamera.tilt). */
    public static final int AUTO = -1, MAX_TILT = 60;

    public record Values(boolean effect, boolean trails, Camera camera, int tilt, Eye eye) {
        public static final Values DEFAULTS = new Values(true, true, Camera.VANILLA, AUTO, Eye.TOWARD_FEET);
        public Values {
            if (camera == null || eye == null) throw new NullPointerException("camera and eye");
            tilt = tilt < 0 ? AUTO : Math.min(tilt, MAX_TILT);
        }
        public Values withEffect(boolean effect) { return new Values(effect, trails, camera, tilt, eye); }
        public Values withTrails(boolean trails) { return new Values(effect, trails, camera, tilt, eye); }
        public Values withCamera(Camera camera) { return new Values(effect, trails, camera, tilt, eye); }
        public Values withTilt(int tilt) { return new Values(effect, trails, camera, tilt, eye); }
        public Values withEye(Eye eye) { return new Values(effect, trails, camera, tilt, eye); }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_trails");
    private static final boolean EFFECT_OFF = Boolean.getBoolean("sleeptrails.disabled");
    private static final boolean TRAILS_OFF = Boolean.getBoolean("sleeptrails.trails.disabled");
    private static volatile Values values = Values.DEFAULTS;
    private static Path file;
    private Settings() {}

    /** Reads the file, or writes the defaults where there is none. A file that cannot be read is left as it is, and the defaults apply. */
    public static void load(Path path) {
        file = path;
        try {
            if (Files.exists(path)) values = parse(Files.readString(path));
            else save();
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Could not read {}; using the default settings", path, error);
            values = Values.DEFAULTS;
        }
    }

    public static Values get() { return values; }
    /** Takes effect at once, and reaches the file on {@link #save()}. */
    public static void change(Values changed) { values = changed; }
    public static boolean effect() { return values.effect() && !EFFECT_OFF; }
    public static boolean trails() { return values.trails() && !TRAILS_OFF; }

    public static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            var temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, write(values));
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            LOGGER.warn("Could not save {}", file, error);
        }
    }

    /** A key that is missing or holds something unexpected keeps its default; the rest still apply. */
    static Values parse(String json) {
        var root = JsonParser.parseString(json).getAsJsonObject();
        var d = Values.DEFAULTS;
        return new Values(bool(root.get("effect"), d.effect()), bool(root.get("trails"), d.trails()),
            choice(root.get("camera"), Camera.class, d.camera()), tilt(root.get("tilt"), d.tilt()),
            choice(root.get("eyePosition"), Eye.class, d.eye()));
    }

    static String write(Values values) {
        var root = new JsonObject();
        root.addProperty("effect", values.effect());
        root.addProperty("trails", values.trails());
        root.addProperty("camera", name(values.camera()));
        if (values.tilt() == AUTO) root.addProperty("tilt", "auto");
        else root.addProperty("tilt", values.tilt());
        root.addProperty("eyePosition", name(values.eye()));
        return new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n";
    }

    public static String name(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }

    private static boolean bool(JsonElement element, boolean fallback) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean() ? element.getAsBoolean() : fallback;
    }

    private static int tilt(JsonElement element, int fallback) {
        if (element == null || !element.isJsonPrimitive()) return fallback;
        var primitive = element.getAsJsonPrimitive();
        if (primitive.isString()) return primitive.getAsString().equals("auto") ? AUTO : fallback;
        return primitive.isNumber() ? Math.clamp(Math.round(primitive.getAsDouble()), 0, MAX_TILT) : fallback;
    }

    private static <E extends Enum<E>> E choice(JsonElement element, Class<E> type, E fallback) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return fallback;
        for (var value : type.getEnumConstants()) if (name(value).equals(element.getAsString())) return value;
        return fallback;
    }
}
