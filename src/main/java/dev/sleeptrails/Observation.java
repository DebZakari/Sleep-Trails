package dev.sleeptrails;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.entity.LivingEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Opt-in fixture evidence. Never records addresses, accounts, chat or arbitrary packet payloads. */
public final class Observation {
    private static final String OUTPUT = System.getProperty("sleeptrails.trace");
    private static final Gson JSON = new Gson();
    private static long sequence;
    private static final long START = System.nanoTime();
    private Observation() {}
    public static boolean enabled() { return OUTPUT != null; }

    public static synchronized void event(String kind, Map<String, ?> fields) {
        if (OUTPUT == null) return;
        var row = new LinkedHashMap<String, Object>();
        row.put("sequence", ++sequence);
        row.put("nanos", System.nanoTime() - START);
        row.put("kind", kind);
        var mc = Minecraft.getInstance();
        if (mc.level != null) {
            row.put("gameTime", mc.level.getGameTime());
            row.put("clock", mc.level.getDefaultClockTime());
            row.put("dimension", mc.level.dimension().identifier().toString());
        }
        if (mc.player != null) row.put("sleeping", mc.player.isSleeping());
        row.putAll(fields);
        try {
            Files.writeString(Path.of(OUTPUT), JSON.toJson(row) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) { throw new IllegalStateException("Cannot write isolated fixture trace", e); }
    }

    public static void time(String stage, ClientboundSetTimePacket packet) {
        if (OUTPUT == null) return;
        var clocks = new LinkedHashMap<String, ClockNetworkState>();
        packet.clockUpdates().forEach((holder, state) -> clocks.put(holder.getRegisteredName(), state));
        event("time_" + stage, Map.of("packetGameTime", packet.gameTime(), "updates", clocks));
    }

    public static void sleep(LivingEntity entity, String kind) {
        if (entity == Minecraft.getInstance().player) event(kind, Map.of());
    }
}
