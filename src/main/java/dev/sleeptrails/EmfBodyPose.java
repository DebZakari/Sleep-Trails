package dev.sleeptrails;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Eye position <i>Vanilla body pose</i>: has Entity Model Features draw the local player's own sleeping body with the vanilla
 * model in the first-person in-bed view, so an animation pack's lying pose keeps the helmet out of the sky (bedoptions-01).
 * F5, other players and the waking player keep the pack's model.
 * <p>
 * EMF 3.3.12's {@code EMFAnimationApi.registerVanillaModelCondition}, reached by reflection, since EMF is optional. EMF asks
 * the condition per entity per frame and keeps it for the session, so it is registered once, the first time it is wanted.
 */
public final class EmfBodyPose {
    private static final Logger LOGGER = LoggerFactory.getLogger("sleep_trails");
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("entity_model_features");
    private static boolean tried;
    private EmfBodyPose() {}

    public static boolean available() { return LOADED; }

    static void install() {
        if (tried || !LOADED) return;
        tried = true;
        try {
            Function<Object, Boolean> condition = entity -> InBedCamera.vanillaPose() && entity == Minecraft.getInstance().player;
            var api = Class.forName("traben.entity_model_features.EMFAnimationApi");
            if (!(boolean)api.getMethod("registerVanillaModelCondition", Function.class).invoke(null, condition))
                LOGGER.warn("Entity Model Features refused the vanilla body pose condition");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            LOGGER.warn("Entity Model Features' animation API is unavailable; the vanilla body pose stays off", error);
        }
    }
}
