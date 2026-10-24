package dev.sleeptrails.mixin;

import dev.sleeptrails.Presentation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.SpatialAttributeInterpolator;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnvironmentAttributeProbe.class)
abstract class CameraEnvironmentMixin {
    @Shadow private Vec3 position;
    @Shadow @Final private SpatialAttributeInterpolator biomeInterpolator;

    @Inject(method = "getValue", at = @At("HEAD"), cancellable = true)
    private <T> void sleeptrails$visual(EnvironmentAttribute<T> attribute, float partialTick, CallbackInfoReturnable<T> cir) {
        if (Presentation.overrides(attribute) && position != null
            && (Object)this == Minecraft.getInstance().gameRenderer.mainCamera().attributeProbe()) {
            cir.setReturnValue(Presentation.value(attribute, position, biomeInterpolator));
        }
    }
}
