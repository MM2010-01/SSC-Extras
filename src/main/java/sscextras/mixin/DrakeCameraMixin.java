package sscextras.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeView;

@Mixin(value = Camera.class, priority = 500)
public abstract class DrakeCameraMixin {
    @Shadow public abstract Vec3d getPos();
    @Shadow protected abstract void setPos(Vec3d position);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private boolean thirdPerson;

    @Inject(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V", ordinal = 0, shift = At.Shift.AFTER))
    private void sscextras$headCamera(BlockView world, Entity entity, boolean thirdPerson,
            boolean inverseView, float tickDelta, CallbackInfo ci) {
        setPos(DrakeView.atHead(entity, tickDelta, getPos()));
    }

    @Inject(method = "update", at = @At("TAIL"))
    private void sscExtras$independentView(BlockView world, Entity entity, boolean thirdPerson,
            boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!sscextras.client.DrakeCameraControl.active(entity)) return;
        if (sscextras.client.DrakeCameraControl.free(entity)) {
            setPos(sscextras.client.DrakeCameraControl.position(tickDelta));
            this.thirdPerson = true;
        }
        setRotation(sscextras.client.DrakeCameraControl.yaw(tickDelta), sscextras.client.DrakeCameraControl.pitch());
    }
}
