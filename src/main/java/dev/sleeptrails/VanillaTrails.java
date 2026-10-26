package dev.sleeptrails;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import dev.sleeptrails.mixin.PipelineCacheAccessor;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Star trails drawn from vanilla's own stars, when no shader pack replaces them (dev.9). One extra draw follows vanilla's
 * star draw, with its blend, brightness and turned sky, and adds the band {@link StarTrailMesh} sweeps behind each star.
 */
public final class VanillaTrails {
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("sleep_trails", "core/star_trails");
    private static final Identifier CLOUD_TEXTURE = Identifier.withDefaultNamespace("textures/environment/clouds.png");
    /** Vanilla's star pipeline with the trail's vertex shader and both faces drawn, since the band's quads face either way. */
    public static final RenderPipeline PIPELINE = RenderPipeline.builder()
        .withBindGroupLayout(BindGroupLayouts.GLOBALS)
        .withBindGroupLayout(BindGroupLayouts.PROJECTION)
        .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
        .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
        .withBindGroupLayout(BindGroupLayouts.FOG)
        .withLocation(Identifier.fromNamespaceAndPath("sleep_trails", "pipeline/star_trails"))
        .withVertexShader(SHADER)
        .withFragmentShader(SHADER)
        .withColorTargetState(new ColorTargetState(BlendFunction.OVERLAY))
        .withCull(false)
        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
        .withPrimitiveTopology(PrimitiveTopology.QUADS)
        .build();
    private static float[] stars;
    private static GpuBuffer mesh;
    private static int indexCount;
    /** The pipeline cache last asked, and its answer: each reload's cache is asked once, so a failed compile is not retried every frame. */
    private static WeakReference<PipelineCache> asked = new WeakReference<>(null);
    private static CompiledRenderPipeline compiled;
    private static long draws;
    private static AbstractTexture cloudTexture;
    private VanillaTrails() {}

    /** Under an unsupported renderer the whole feature is off, and the trails add nothing, not even a pipeline. */
    public static boolean wanted() { return RendererCompatibility.supportedRenderer() && !RendererCompatibility.irisPresent(); }

    /** Keeps a copy of vanilla's star corners as its buffer is built. Another layout than vanilla's is left alone. */
    public static void capture(ByteBuffer data) {
        close();
        stars = null;
        int bytes = data.remaining();
        if (!wanted() || DefaultVertexFormat.POSITION.getVertexSize() != 12 || bytes % 48 != 0) return;
        float[] corners = new float[bytes / 4];
        data.duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer().get(corners);
        stars = corners;
    }

    /** Builds the mesh outside any render pass, on the first frame that wants it. A pack that draws the stars keeps it unbuilt. */
    public static void prepare() {
        if (cloudTexture == null && !packDrawing())
            cloudTexture = Minecraft.getInstance().getTextureManager().getTexture(CLOUD_TEXTURE);
        if (mesh != null || stars == null || packDrawing()) return;
        float[] band = StarTrailMesh.build(stars, StarTrailMesh.STEPS);
        if (band.length == 0) return;
        ByteBuffer data = MemoryUtil.memAlloc(band.length * 4);
        try {
            data.order(ByteOrder.nativeOrder()).asFloatBuffer().put(band);
            mesh = RenderSystem.getDevice().createBuffer(() -> "Star trails vertex buffer", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, data);
        } finally {
            MemoryUtil.memFree(data);
        }
        indexCount = band.length / StarTrailMesh.STRIDE / 4 * 6;
        // Grown here, outside the sky pass, so the draw never resizes the shared index buffer under it.
        RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(indexCount);
    }

    /** Runs in vanilla's star draw, right after it, with the star pose still on the model-view stack. */
    public static void draw(RenderPass pass, float brightness) {
        float arc = Presentation.starArc();
        if (arc <= 0 || mesh == null || packDrawing()) return;
        CompiledRenderPipeline pipeline = pipeline();
        if (pipeline == null) return;
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var state = Minecraft.getInstance().gameRenderer.gameRenderState();
        var level = state.levelRenderState;
        var options = state.optionsRenderState;
        var texture = cloudTexture.getTextureView();
        // Transform star-local positions to world rays; the last column holds cloud-space camera position and range.
        Matrix4f rayTransform = new Matrix4f(level.cameraRenderState.viewRotationMatrix).invert()
            .mul(new Matrix4f(RenderSystem.getModelViewStack()));
        double time = cloudAnimationTime(level.gameTime, level.worldPartialTicks);
        long wholeTime = (long)Math.floor(time);
        float cloudTime = (float)Math.floorMod(wholeTime, texture.getWidth(0) * 400L) + (float)(time - wholeTime);
        var camera = level.cameraRenderState.pos;
        double x = camera.x + cloudTime * 0.030000001F;
        double z = camera.z + 3.96F;
        x -= Math.floor(x / (texture.getWidth(0) * 12.0)) * texture.getWidth(0) * 12.0;
        z -= Math.floor(z / (texture.getHeight(0) * 12.0)) * texture.getHeight(0) * 12.0;
        rayTransform.m30((float)x).m31((float)(camera.y - level.cloudHeight)).m32((float)z)
            .m33((float)Math.ceil(options.cloudRange * 16.0 / 12.0));
        int cloudMode = options.cloudStatus == CloudStatus.OFF ? 0 : options.cloudStatus == CloudStatus.FANCY ? 2 : 1;
        // ModelOffset carries arc, cloud geometry mode and cloud alpha; TextureMat carries the ray transform.
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(RenderSystem.getModelViewStack()),
            new Vector4f(brightness, brightness, brightness, brightness), new Vector3f(arc, cloudMode, ARGB.alpha(level.cloudColor) / 255.0F), rayTransform);
        pass.pushDebugGroup(() -> "Star trails");
        pass.setPipeline(pipeline);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setUniform("Sampler0", texture, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        pass.setVertexBuffer(0, mesh.slice());
        pass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
        pass.drawIndexed(indexCount, 1, 0, 0, 0);
        pass.popDebugGroup();
        draws++;
    }

    private static CompiledRenderPipeline pipeline() {
        PipelineCache cache = PipelineCacheAccessor.sleeptrails$current();
        if (cache != asked.get()) {
            asked = new WeakReference<>(cache);
            compiled = cache == null ? null : cache.get(PIPELINE);
            if (cache != null && compiled == null)
                LoggerFactory.getLogger("sleep_trails").warn("The star trail pipeline did not compile; vanilla stars stay instantaneous until the next resource reload");
        }
        return compiled;
    }

    public static void close() {
        if (mesh != null) mesh.close();
        mesh = null;
        cloudTexture = null;
    }

    /** The trail shaders, from this JAR, for a game without Fabric API's resource loading. */
    public static String shader(Identifier id, ShaderType type) {
        if (!id.equals(SHADER)) return null;
        try (InputStream in = VanillaTrails.class.getResourceAsStream("/assets/sleep_trails/shaders/core/star_trails."
            + (type == ShaderType.VERTEX ? "vsh" : "fsh"))) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            return null;
        }
    }

    /** With the exact Vitrail, whether a pack is loaded and drawing, in which case the pack draws the sky and its stars. */
    public static boolean packDrawing() {
        return RendererCompatibility.vitrailPresent() && dev.sleeptrails.vitrail.PackState.drawing();
    }

    /** Trail draws so far, for the fixture trace. */
    public static long draws() { return draws; }
    /** Same elapsed animation input supplied to vanilla clouds, independent of the sky's day clock. */
    public static double cloudAnimationTime(long gameTime, float partialTick) {
        return gameTime + (double)partialTick + Presentation.cloudOffset();
    }
}
