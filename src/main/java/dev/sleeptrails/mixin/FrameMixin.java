package dev.sleeptrails.mixin;

import dev.sleeptrails.Presentation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class FrameMixin {
    /** update runs just before extract each frame, and places the camera, so the in-bed camera reads this frame's clock. */
    @Inject(method = "update", at = @At("HEAD"))
    private void sleeptrails$frame(DeltaTracker tracker, CallbackInfo ci) {
        Presentation.beginFrame(tracker.getGameTimeDeltaPartialTick(false));
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void sleeptrails$close(CallbackInfo ci) { Presentation.reset(); }

    @Inject(method = "resize", at = @At("HEAD"))
    private void sleeptrails$resize(int width, int height, CallbackInfo ci) { Presentation.reset(); }

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void sleeptrails$reload(ResourceManager resources, CallbackInfo ci) { Presentation.reset(); }
}
