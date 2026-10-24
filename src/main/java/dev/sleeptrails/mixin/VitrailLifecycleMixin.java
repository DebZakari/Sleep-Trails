package dev.sleeptrails.mixin;

import dev.sleeptrails.Presentation;
import dev.vitrail.render.PackChoice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.file.Path;

/** Exact-version optional hook shared by pack reload, switching and shader off/on. */
@Pseudo
@Mixin(value = PackChoice.class, remap = false)
abstract class VitrailLifecycleMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private static void sleeptrails$reload(Path directory, CallbackInfo ci) { Presentation.reset(); }
}
