package dev.sleeptrails.config;

import com.mojang.serialization.Codec;
import dev.sleeptrails.EmfBodyPose;
import dev.sleeptrails.InBedCamera;
import dev.sleeptrails.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * The settings as a plain vanilla options screen, which Mod Menu opens. It needs no other mod, so it works on every renderer.
 * Changes apply as they are made and reach the file when the screen closes.
 */
public final class SettingsScreen extends OptionsSubScreen {
    private OptionInstance<Integer> tilt;

    public SettingsScreen(Screen parent) {
        super(parent, Minecraft.getInstance().options, Component.translatable(Text.TITLE));
    }

    @Override
    protected void addOptions() {
        var s = Settings.get();
        list.addSmall(
            OptionInstance.createBoolean(Text.EFFECT, tooltip(Text.EFFECT), s.effect(), v -> Settings.change(Settings.get().withEffect(v))),
            OptionInstance.createBoolean(Text.TRAILS, tooltip(Text.TRAILS), s.trails(), v -> Settings.change(Settings.get().withTrails(v))));
        list.addBig(OptionInstance.createBoolean(Text.TIME_JUMPS, tooltip(Text.TIME_JUMPS), s.timeJumps(),
            v -> Settings.change(Settings.get().withTimeJumps(v))));
        list.addHeader(Component.translatable(Text.CAMERA_HEADER));
        tilt = new OptionInstance<>(Text.TILT, OptionInstance.cachedConstantTooltip(Component.translatable(Text.tooltip(Text.TILT))),
            (caption, v) -> Options.genericValueLabel(caption, Text.tilt(v, options.fov().get())),
            new OptionInstance.IntRange(Settings.AUTO, Settings.MAX_TILT), s.tilt(), v -> Settings.change(Settings.get().withTilt(v)));
        list.addSmall(
            choice(Text.CAMERA, Settings.Camera.values(), s.camera(), v -> {
                Settings.change(Settings.get().withCamera(v));
                updateTilt();
            }),
            tilt);
        // A row of its own: "Eye Position: Toward the Feet" is wider than a half-row button (dev.7 scrolled it).
        var eyes = Arrays.stream(Settings.Eye.values()).filter(e -> e != Settings.Eye.VANILLA_POSE || EmfBodyPose.available()).toList();
        list.addBig(choice(Text.EYE, eyes.toArray(Settings.Eye[]::new), eyes.contains(s.eye()) ? s.eye() : Settings.Eye.VANILLA,
            v -> Settings.change(Settings.get().withEye(v))));
        updateTilt();
    }

    /** The tilt is the Vanilla mode's alone. */
    private void updateTilt() {
        var widget = list.findOption(tilt);
        if (widget != null) widget.active = Settings.get().camera() == Settings.Camera.VANILLA;
    }

    @Override
    public void removed() {
        Settings.save();
        super.removed();
    }

    private static <T> OptionInstance.TooltipSupplier<T> tooltip(String key) {
        return OptionInstance.cachedConstantTooltip(Component.translatable(Text.tooltip(key)));
    }

    private static <E extends Enum<E>> OptionInstance<E> choice(String key, E[] values, E initial, OptionInstance.ValueUpdateListener<E> listener) {
        Function<E, Component> name = v -> Component.translatable(Text.value(key, v));
        return new OptionInstance<>(key, v -> Tooltip.create(Component.translatable(Text.tooltip(Text.value(key, v)))),
            (caption, v) -> name.apply(v), new OptionInstance.Enum<>(List.of(values), Codec.INT.xmap(i -> values[i], Enum::ordinal)),
            initial, listener);
    }

    /** The translation keys, shared with the Sodium page. */
    static final class Text {
        static final String TITLE = "sleep_trails.settings", PAGE = "sleep_trails.settings.page", EFFECT = "sleep_trails.settings.effect", TRAILS = "sleep_trails.settings.trails",
            TIME_JUMPS = "sleep_trails.settings.time_jumps", CAMERA_HEADER = "sleep_trails.settings.in_bed_camera", CAMERA = "sleep_trails.settings.camera",
            TILT = "sleep_trails.settings.tilt", EYE = "sleep_trails.settings.eye_position";
        private Text() {}
        static String tooltip(String key) { return key + ".tooltip"; }
        static String value(String key, Enum<?> value) { return key + "." + Settings.name(value); }
        static Component tilt(int value, int fov) {
            return value < 0 ? Component.translatable(TILT + ".auto", InBedCamera.tilt(Settings.AUTO, fov))
                : value == 0 ? Component.translatable(TILT + ".vanilla") : Component.translatable(TILT + ".degrees", value);
        }
    }
}
