package dev.sleeptrails.config;

import dev.sleeptrails.EmfBodyPose;
import dev.sleeptrails.Settings;
import dev.sleeptrails.config.SettingsScreen.Text;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.EnumSet;

/**
 * The same settings as a page in Sodium's Video Settings, beside Vitrail's own, through Sodium's config API
 * ({@code sodium:config_api_user}). Sodium alone loads this class, so it stays optional.
 */
public final class SodiumEntry implements ConfigEntryPoint {
    /** The mod's icon, which Sodium blits whole as a plain texture. */
    private static final Identifier ICON = id("icon.png");
    /** The dawn glow along the icon's horizon. */
    private static final int THEME = 0xFFE88C5C;
    private static final Identifier EFFECT = id("effect"), TRAILS = id("trails"), TIME_JUMPS = id("time_jumps"), CAMERA = id("camera"), TILT = id("tilt"), EYE = id("eye_position");

    @Override
    public void registerConfigLate(ConfigBuilder builder) {
        var defaults = Settings.Values.DEFAULTS;
        var eyes = EmfBodyPose.available() ? EnumSet.allOf(Settings.Eye.class) : EnumSet.complementOf(EnumSet.of(Settings.Eye.VANILLA_POSE));
        builder.registerOwnModOptions()
            .setNonTintedIcon(ICON)
            .setColorTheme(builder.createColorTheme().setBaseThemeRGB(THEME))
            .addPage(builder.createOptionPage()
                .setName(Component.translatable(Text.PAGE))
                .addOptionGroup(builder.createOptionGroup()
                    .addOption(builder.createBooleanOption(EFFECT)
                        .setName(Component.translatable(Text.EFFECT))
                        .setTooltip(Component.translatable(Text.tooltip(Text.EFFECT)))
                        .setDefaultValue(defaults.effect())
                        .setBinding(v -> Settings.change(Settings.get().withEffect(v)), () -> Settings.get().effect())
                        .setStorageHandler(Settings::save))
                    .addOption(builder.createBooleanOption(TIME_JUMPS)
                        .setName(Component.translatable(Text.TIME_JUMPS))
                        .setTooltip(Component.translatable(Text.tooltip(Text.TIME_JUMPS)))
                        .setDefaultValue(defaults.timeJumps())
                        .setBinding(v -> Settings.change(Settings.get().withTimeJumps(v)), () -> Settings.get().timeJumps())
                        .setStorageHandler(Settings::save))
                    .addOption(builder.createBooleanOption(TRAILS)
                        .setName(Component.translatable(Text.TRAILS))
                        .setTooltip(Component.translatable(Text.tooltip(Text.TRAILS)))
                        .setDefaultValue(defaults.trails())
                        .setBinding(v -> Settings.change(Settings.get().withTrails(v)), () -> Settings.get().trails())
                        .setStorageHandler(Settings::save)))
                .addOptionGroup(builder.createOptionGroup()
                    .setName(Component.translatable(Text.CAMERA_HEADER))
                    .addOption(builder.createEnumOption(CAMERA, Settings.Camera.class)
                        .setName(Component.translatable(Text.CAMERA))
                        .setTooltip(v -> Component.translatable(Text.tooltip(Text.value(Text.CAMERA, v))))
                        .setElementNameProvider(v -> Component.translatable(Text.value(Text.CAMERA, v)))
                        .setDefaultValue(defaults.camera())
                        .setBinding(v -> Settings.change(Settings.get().withCamera(v)), () -> Settings.get().camera())
                        .setStorageHandler(Settings::save))
                    .addOption(builder.createIntegerOption(TILT)
                        .setName(Component.translatable(Text.TILT))
                        .setTooltip(Component.translatable(Text.tooltip(Text.TILT)))
                        .setRange(Settings.AUTO, Settings.MAX_TILT, 1)
                        .setValueFormatter(v -> Text.tilt(v, Minecraft.getInstance().options.fov().get()))
                        .setDefaultValue(defaults.tilt())
                        .setBinding(v -> Settings.change(Settings.get().withTilt(v)), () -> Settings.get().tilt())
                        .setStorageHandler(Settings::save)
                        // The tilt is the Vanilla mode's alone.
                        .setEnabledProvider(state -> state.readEnumOption(CAMERA, Settings.Camera.class) == Settings.Camera.VANILLA, CAMERA))
                    .addOption(builder.createEnumOption(EYE, Settings.Eye.class)
                        .setName(Component.translatable(Text.EYE))
                        .setTooltip(v -> Component.translatable(Text.tooltip(Text.value(Text.EYE, v))))
                        .setElementNameProvider(v -> Component.translatable(Text.value(Text.EYE, v)))
                        .setAllowedValues(eyes)
                        .setDefaultValue(defaults.eye())
                        .setBinding(v -> Settings.change(Settings.get().withEye(v)),
                            () -> eyes.contains(Settings.get().eye()) ? Settings.get().eye() : Settings.Eye.VANILLA)
                        .setStorageHandler(Settings::save))));
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("sleep_trails", path); }
}
