package dev.sleeptrails;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @Test void everyValueSurvivesTheFile() {
        for (var camera : Settings.Camera.values()) for (var eye : Settings.Eye.values()) for (int tilt : new int[] {Settings.AUTO, 0, 35, 60}) {
            var values = new Settings.Values(tilt != 35, tilt == 35, camera, tilt, eye);
            assertEquals(values, Settings.parse(Settings.write(values)));
        }
    }

    @Test void theFileIsReadableByHand() {
        assertEquals("""
            {
              "effect": true,
              "trails": true,
              "camera": "vanilla",
              "tilt": "auto",
              "eyePosition": "toward_feet"
            }
            """, Settings.write(Settings.Values.DEFAULTS));
    }

    @Test void aBadOrMissingKeyKeepsItsDefaultAndTheRestApply() {
        var values = Settings.parse("{\"effect\": \"no\", \"trails\": false, \"camera\": \"orbit\", \"tilt\": 90.4, \"extra\": 1}");
        assertEquals(new Settings.Values(true, false, Settings.Camera.VANILLA, Settings.MAX_TILT, Settings.Eye.TOWARD_FEET), values);
        assertEquals(0, Settings.parse("{\"tilt\": -12}").tilt());
        assertEquals(Settings.AUTO, Settings.parse("{\"tilt\": \"sideways\"}").tilt());
        assertEquals(Settings.Values.DEFAULTS, Settings.parse("{}"));
    }

    @Test void aMissingFileGetsTheDefaultsAndAnUnreadableOneIsLeftAlone(@TempDir Path dir) throws Exception {
        var file = dir.resolve("config/sleep_trails.json");
        Settings.load(file);
        assertEquals(Settings.Values.DEFAULTS, Settings.get());
        assertEquals(Settings.write(Settings.Values.DEFAULTS), Files.readString(file));

        Settings.change(Settings.get().withCamera(Settings.Camera.CINEMATIC).withTilt(20));
        Settings.save();
        Settings.load(file);
        assertEquals(Settings.Camera.CINEMATIC, Settings.get().camera());
        assertEquals(20, Settings.get().tilt());

        Files.writeString(file, "{ not json");
        Settings.load(file);
        assertEquals(Settings.Values.DEFAULTS, Settings.get());
        assertEquals("{ not json", Files.readString(file));
    }
}
