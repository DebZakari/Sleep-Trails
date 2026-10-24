package dev.sleeptrails.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sleeptrails.Presentation;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Adapt only vanilla cloud animation, leaving the world's game time and other render inputs untouched. */
@Mixin(LevelRenderer.class)
abstract class CloudTimeMixin {
    @WrapOperation(method = "prepareTranslucents", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/CloudRenderer;prepare(ILnet/minecraft/client/CloudStatus;FILnet/minecraft/world/phys/Vec3;JF)V"))
    private void sleeptrails$cloudTime(CloudRenderer clouds, int color, CloudStatus status, float height, int range,
                                     Vec3 camera, long gameTime, float partialTick, Operation<Void> original) {
        double offset = Presentation.cloudOffset();
        long whole = (long)Math.floor(offset);
        double partial = partialTick + (offset - whole);
        long carry = (long)Math.floor(partial);
        original.call(clouds, color, status, height, range, camera, gameTime + whole + carry, (float)(partial - carry));
    }
}
