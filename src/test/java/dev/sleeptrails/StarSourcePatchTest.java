package dev.sleeptrails;

import dev.sleeptrails.vitrail.StarSourcePatch;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class StarSourcePatchTest {
    @Test void adapterRetainsTheEntireOriginalStarFunctionAndOnlyWrapsItsName() throws Exception {
        var path = Path.of(System.getProperty("sleeptrails.referencePack"));
        try (var zip = new ZipFile(path.toFile())) {
            byte[] bytes = zip.getInputStream(zip.getEntry("shaders/lib/atmospherics/stars.glsl")).readAllBytes();
            assertEquals(StarSourcePatch.STARS_SHA, StarSourcePatch.sha(bytes));
            String original = new String(bytes, StandardCharsets.UTF_8);
            String result = StarSourcePatch.transform(original);
            assertTrue(result.startsWith(original.replace("vec3 GetStars(", "vec3 SleepTrailsInstantStars(")));
            assertTrue(result.contains("SleepTrailsInstantStars(at, VdotU, VdotS) * weight"));
            assertTrue(result.contains("return max(instant, energy)"));
        }
    }
    @Test void changedFunctionShapesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> StarSourcePatch.transform("unknown"));
        String duplicate = "vec3 GetStars(vec2 starCoord, float VdotU, float VdotS)";
        assertThrows(IllegalArgumentException.class, () -> StarSourcePatch.transform(duplicate + duplicate));
    }
}
