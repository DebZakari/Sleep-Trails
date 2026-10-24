package dev.sleeptrails.mixin;

import dev.sleeptrails.Presentation;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapRefreshMixin {
    @Shadow private boolean needsUpdate;
    @Unique private boolean sleeptrails$wasActive;

    @Inject(method = "extract", at = @At("HEAD"))
    private void sleeptrails$refresh(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        boolean active = Presentation.frame().active();
        // Fast presentation needs a matching lightmap each frame, including the return to real time.
        if (active || sleeptrails$wasActive) needsUpdate = true;
        sleeptrails$wasActive = active;
    }
}
