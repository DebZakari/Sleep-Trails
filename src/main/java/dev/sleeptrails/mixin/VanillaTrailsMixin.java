package dev.sleeptrails.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import dev.sleeptrails.VanillaTrails;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.function.Supplier;

@Mixin(SkyRenderer.class)
abstract class VanillaTrailsMixin {
    @WrapOperation(method = "buildStars", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/device/GpuDevice;createBuffer(Ljava/util/function/Supplier;ILjava/nio/ByteBuffer;)Lcom/mojang/renderpearl/api/buffers/GpuBuffer;"))
    private GpuBuffer sleeptrails$capture(GpuDevice device, Supplier<String> label, int usage, ByteBuffer data, Operation<GpuBuffer> original) {
        VanillaTrails.capture(data);
        return original.call(device, label, usage, data);
    }

    /** After vanilla's own star draw, in its pass, with its brightness and the turned sky still on the model-view stack. */
    @Inject(method = "renderStars", at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V", shift = At.Shift.AFTER))
    private void sleeptrails$trails(RenderPass pass, float brightness, PoseStack pose, CallbackInfo ci) {
        VanillaTrails.draw(pass, brightness);
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void sleeptrails$close(CallbackInfo ci) { VanillaTrails.close(); }
}
