package dev.sleeptrails;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point for the client-only presentation prototype. */
public final class SleepTrailsClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_trails");

    @Override
    public void onInitializeClient() {
        Settings.load(FabricLoader.getInstance().getConfigDir().resolve("sleep_trails.json"));
        LOGGER.info("Sleep Trails in-bed transition and exact-pack star-exposure prototype loaded; renderer supported: {}; settings: {}",
            VitrailCompatibility.supportedRenderer(), Settings.get());
    }
}
