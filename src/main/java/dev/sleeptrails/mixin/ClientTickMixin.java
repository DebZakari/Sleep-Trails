package dev.sleeptrails.mixin;

import dev.sleeptrails.Presentation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class ClientTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void sleeptrails$tick(CallbackInfo ci) { Presentation.tick(); }
}
