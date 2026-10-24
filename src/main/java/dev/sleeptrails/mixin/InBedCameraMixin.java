package dev.sleeptrails.mixin;

import dev.sleeptrails.InBedCamera;
import dev.sleeptrails.Settings;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Places the in-bed camera (InBedCamera) after vanilla has placed its own. */
@Mixin(Camera.class)
abstract class InBedCameraMixin {
    @Shadow private Entity entity;
    @Shadow private boolean detached;
    @Shadow private float xRot;
    @Shadow private float yRot;
    @Shadow private Vec3 position;
    @Shadow protected abstract void setRotation(float yRot, float xRot);
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void move(float forwards, float up, float right);
    @Shadow private float getMaxZoom(float distance) { throw new AssertionError(); }

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void sleeptrails$inBed(float partialTicks, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        var bed = entity == mc.player && mc.player.isSleeping() ? mc.player.getBedOrientation() : null;
        long now = System.nanoTime();
        double seconds = InBedCamera.seconds(bed != null, detached, now);
        if (Double.isNaN(seconds)) {
            var eased = detached ? InBedCamera.easeBack(new InBedCamera.Pose(position, yRot, xRot), now) : null;
            if (eased != null) { setRotation(eased.yRot(), eased.xRot()); setPosition(eased.position()); }
            return;
        }
        var settings = Settings.get();
        int fov = mc.options.fov().get();
        if (!detached) {
            InBedCamera.forget();
            // Vanilla's eye and a zero tilt leave vanilla's camera untouched.
            var offset = InBedCamera.eyeOffset(settings.eye(), yRot);
            var eye = position.add(offset);
            var block = BlockPos.containing(eye);
            var shape = entity.level().getBlockState(block).getCollisionShape(entity.level(), block).move(block);
            // The eye stays where vanilla put it rather than enter a solid block. Vanilla's own eye sits in the bed's block
            // space, above the mattress, so the test is the point against the block's boxes rather than whether it has any.
            if (offset.lengthSqr() > 0 && shape.toAabbs().stream().noneMatch(box -> box.contains(eye))) setPosition(eye);
            float pitch = InBedCamera.firstPersonPitch(settings, seconds, fov);
            if (pitch != 0) setRotation(yRot, pitch);
            return;
        }
        if (settings.camera() != Settings.Camera.CINEMATIC) return;
        var orbit = InBedCamera.orbit(entity.getPosition(partialTicks), bed, seconds, fov);
        // Back away from the pivot as vanilla's third person does, stopping short of blocks in the way.
        setRotation(orbit.yaw(), orbit.look());
        setPosition(orbit.pivot());
        move(-getMaxZoom((float)orbit.reach()), 0, 0);
        setRotation(orbit.yaw(), orbit.pitch());
        InBedCamera.remember(new InBedCamera.Pose(position, yRot, xRot));
    }
}
