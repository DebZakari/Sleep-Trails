package dev.sleeptrails.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.sleeptrails.VanillaTrails;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.stream.Stream;

/** Compiles the trail pipeline with every reload, as an optional pipeline: a failure there is only a warning. */
@Mixin(RenderPipelines.class)
abstract class TrailPipelineMixin {
    @ModifyReturnValue(method = "optionalPipelines", at = @At("RETURN"))
    private static List<RenderPipeline> sleeptrails$trails(List<RenderPipeline> pipelines) {
        return VanillaTrails.wanted() ? Stream.concat(pipelines.stream(), Stream.of(VanillaTrails.PIPELINE)).toList() : pipelines;
    }
}
