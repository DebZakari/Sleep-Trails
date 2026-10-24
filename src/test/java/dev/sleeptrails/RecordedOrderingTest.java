package dev.sleeptrails;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class RecordedOrderingTest {
    @Test void replayOfRealUnmodifiedServerTrafficStartsExactlyTheTwoSuccessfulSleeps() throws Exception {
        var controller = new PresentationClock(5_000_000_000L, 250_000_000L, 1_500_000_000L);
        controller.context(new Object(), true);
        var starts = new TreeMap<String, Integer>();
        try (var input = getClass().getResourceAsStream("/ordering-26.3.json")) {
            assertNotNull(input);
            var events = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("events");
            for (var entry : events) {
                var event = entry.getAsJsonObject();
                long now = event.get("nanos").getAsLong();
                String name = event.get("case").getAsString();
                if (event.get("kind").getAsString().equals("sleep")) {
                    controller.observeSleep(event.get("sleeping").getAsBoolean(), event.get("clock").getAsDouble(), now);
                } else {
                    controller.frame(now, event.get("before").getAsDouble());
                    if (controller.update(event.get("before").getAsDouble(), event.get("after").getAsDouble(),
                        event.get("rate").getAsFloat(), now)) starts.merge(name, 1, Integer::sum);
                }
            }
        }
        assertEquals(Map.of("early_sleep", 1, "late_sleep", 1), starts);
    }
}
