package dev.sleeptrails.mixin;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The current reload's pipeline cache, so the trail pipeline is looked up once per reload (VanillaTrails). */
@Mixin(RenderSystem.class)
public interface PipelineCacheAccessor {
    @Accessor("currentPipelineCache")
    static PipelineCache sleeptrails$current() { throw new AssertionError(); }
}
