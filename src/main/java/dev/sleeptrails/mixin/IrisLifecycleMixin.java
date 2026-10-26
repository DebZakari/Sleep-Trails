package dev.sleeptrails.mixin;

import dev.sleeptrails.Presentation;
import net.irisshaders.iris.Iris;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shader reload, selection changes and off/on all pass through this exact-version boundary. */
@Pseudo
@Mixin(value = Iris.class, remap = false)
abstract class IrisLifecycleMixin {
    @Inject(method = "reload", at = @At("HEAD"))
    private static void sleeptrails$reload(CallbackInfo ci) { Presentation.reset(); }
}
