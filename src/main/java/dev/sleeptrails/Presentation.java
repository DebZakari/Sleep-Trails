package dev.sleeptrails;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.attribute.*;
import net.minecraft.world.clock.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/** A separate environment sampler used only by camera rendering and the guarded shader adapter. */
public final class Presentation {
    private static final SkyTransition TRANSITION = new SkyTransition();
    private static ClientLevel world;
    private static EnvironmentAttributeSystem environment;
    /** The same sky at the start of the star exposure, for the arc vanilla's stars turned through (dev.9). */
    private static EnvironmentAttributeSystem exposure;
    private static Set<EnvironmentAttribute<?>> visualTracks = Set.of();
    private static long frameIndex;
    private Presentation() {}

    private static boolean supported(ClientLevel level) {
        return level != null && level.dimension().equals(Level.OVERWORLD)
            && !level.dimensionType().hasFixedTime()
            && level.dimensionType().defaultClock().filter(clock -> clock.is(WorldClocks.OVERWORLD)
                && level.clockManager().getInstance(clock).rate() == 1).isPresent();
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (world != mc.level) {
            world = mc.level;
            environment = null;
            exposure = null;
            visualTracks = Set.of();
        }
        long now = System.nanoTime();
        double time = world == null ? 0 : world.getDefaultClockTime();
        var started = TRANSITION.observe(world, supported(world) && VitrailCompatibility.supportedRenderer(),
            mc.player != null && mc.player.isSleeping(), time, now,
            new SkyTransition.Options(Settings.effect(), Settings.timeJumps(), Settings.trails()));
        if (started != null) {
            Observation.event("sleep_preview_start", Map.of("from", time, "visible", started.visible()));
        }
    }

    public static void beforeTime(ClientboundSetTimePacket packet) {
        tick();
        if (world == null || !supported(world)) return;
        Holder<WorldClock> holder = world.dimensionType().defaultClock().orElseThrow();
        ClockNetworkState update = packet.clockUpdates().get(holder);
        if (update == null) return;
        long now = System.nanoTime();
        double before = world.getDefaultClockTime();
        double after = update.totalTicks() + update.partialTick();
        var started = TRANSITION.timeUpdate(before, after, update.rate(), now);
        if (started != null) {
            Observation.event(started.kind() == SkyTransition.StartKind.AWAKE ? "time_jump_start" : "catchup_start",
                Map.of("from", before, "target", update.totalTicks(), "finishFrom", started.finishFrom()));
        }
    }

    public static void beginFrame(float partialTick) {
        tick();
        var mc = Minecraft.getInstance();
        double authoritative = 0;
        if (world != null && world.dimensionType().defaultClock().isPresent()) {
            var clock = world.clockManager().getInstance(world.dimensionType().defaultClock().orElseThrow());
            authoritative = clock.totalTicks() + clock.partialTick() + partialTick * clock.rate();
        }
        long now = System.nanoTime();
        var snapshot = TRANSITION.beginFrame(now, authoritative, world == null ? 0 : world.getGameTime() + (double)partialTick);
        var frame = snapshot.sky();
        if (frame.active()) {
            if (environment == null) buildEnvironments();
            environment.invalidateTickCache();
            exposure.invalidateTickCache();
            if (snapshot.exposure().ticks() > 0) VanillaTrails.prepare();
        }
        if (Observation.enabled() && ++frameIndex % 6 == 0 && mc.player != null && world != null) {
            var camera = mc.gameRenderer.mainCamera();
            var raw = world.environmentAttributes();
            var fields = new java.util.LinkedHashMap<String, Object>();
            fields.put("active", frame.active());
            fields.put("presentationTime", frame.time());
            fields.put("motionTime", frame.motionTime());
            fields.put("authoritativeTime", authoritative);
            fields.put("progress", frame.progress());
            fields.put("generation", frame.generation());
            fields.put("exposureTicks", snapshot.exposure().ticks());
            fields.put("starArc", starArc());
            fields.put("vanillaTrailDraws", VanillaTrails.draws());
            fields.put("confirmed", snapshot.confirmed());
            fields.put("returning", snapshot.returning());
            fields.put("awakeTransition", snapshot.awake());
            fields.put("playerTick", mc.player.tickCount);
            fields.put("realSun", raw.getValue(EnvironmentAttributes.SUN_ANGLE, camera.position(), null));
            fields.put("visualSun", valueOrReal(EnvironmentAttributes.SUN_ANGLE, camera.position()));
            fields.put("realSkyFactor", raw.getValue(EnvironmentAttributes.SKY_LIGHT_FACTOR, camera.position(), null));
            fields.put("visualSkyFactor", valueOrReal(EnvironmentAttributes.SKY_LIGHT_FACTOR, camera.position()));
            fields.put("realSkyLightLevel", raw.getDimensionValue(EnvironmentAttributes.SKY_LIGHT_LEVEL));
            Observation.event("frame", fields);
        }
    }

    private static void buildEnvironments() {
        var tracks = new HashSet<EnvironmentAttribute<?>>();
        environment = buildEnvironment(() -> frame().time(), tracks);
        exposure = buildEnvironment(() -> frame().time() - exposure().ticks(), new HashSet<>());
        visualTracks = Set.copyOf(tracks);
    }

    private static EnvironmentAttributeSystem buildEnvironment(DoubleSupplier time, Set<EnvironmentAttribute<?>> tracks) {
        var builder = EnvironmentAttributeSystem.builder().addStaticLayers(world);
        ClockInstance visualClock = new ClockInstance() {
            public long totalTicks() { return (long)Math.floor(time.getAsDouble()); }
            public float partialTick() { double t = time.getAsDouble(); return (float)(t - Math.floor(t)); }
            public float rate() { return 1; }
            public boolean isPaused() { return false; }
        };
        ClockManager clocks = definition -> definition.is(WorldClocks.OVERWORLD)
            ? visualClock : world.clockManager().getInstance(definition);
        world.dimensionType().timelines().forEach(timeline -> {
            builder.addTimelineLayer(timeline, clocks);
            timeline.value().attributes().forEach(attribute -> {
                var id = BuiltInRegistries.ENVIRONMENT_ATTRIBUTE.getKey(attribute);
                if (id != null && id.getPath().startsWith("visual/")
                    && attribute != EnvironmentAttributes.AMBIENT_PARTICLES
                    && attribute != EnvironmentAttributes.DEFAULT_DRIPSTONE_PARTICLE) tracks.add(attribute);
            });
        });
        if (world.canHaveWeather()) WeatherAttributes.addBuiltinLayers(builder, WeatherAttributes.WeatherAccess.from(world));
        return builder.build();
    }

    /**
     * The arc vanilla's stars turned through over this frame's exposure, in radians: the presented star angle less the one
     * at the exposure's start, both read from the same timelines at the camera, at whole ticks as the sky reads them.
     * 0 when inactive.
     */
    public static float starArc() {
        if (!frame().active() || environment == null || exposure().ticks() <= 0) return 0;
        Vec3 at = Minecraft.getInstance().gameRenderer.mainCamera().position();
        return StarExposure.arc(environment.getValue(EnvironmentAttributes.STAR_ANGLE, at, null),
            exposure.getValue(EnvironmentAttributes.STAR_ANGLE, at, null));
    }

    public static boolean overrides(EnvironmentAttribute<?> attribute) {
        return frame().active() && environment != null && visualTracks.contains(attribute);
    }

    public static <T> T value(EnvironmentAttribute<T> attribute, Vec3 pos, SpatialAttributeInterpolator weights) {
        return environment.getValue(attribute, pos, weights);
    }

    private static <T> T valueOrReal(EnvironmentAttribute<T> attribute, Vec3 pos) {
        return overrides(attribute) ? value(attribute, pos, null) : world.environmentAttributes().getValue(attribute, pos, null);
    }

    public static PresentationClock.Frame frame() { return TRANSITION.snapshot().sky(); }
    public static StarExposure.Sample exposure() { return TRANSITION.snapshot().exposure(); }
    /** Extra animation time for vanilla clouds, retained after the sleep to preserve their position. */
    public static double cloudOffset() { return TRANSITION.snapshot().cloudOffset(); }
    /** This frame's seconds into the sleep's preview: NaN without one, infinite once the skip is confirmed. */
    public static double previewSeconds() { return TRANSITION.snapshot().previewSeconds(); }
    public static long shaderTime(long authoritative) { return TRANSITION.snapshot().shaderTime(authoritative); }
    public static void reset() { TRANSITION.cancel(); environment = null; exposure = null; }
}
