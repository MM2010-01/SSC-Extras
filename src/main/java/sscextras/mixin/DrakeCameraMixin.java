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

@Mixin(Camera.class)
public abstract class DrakeCameraMixin {
    @Shadow public abstract Vec3d getPos();
    @Shadow protected abstract void setPos(Vec3d position);

    @Inject(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V", ordinal = 0, shift = At.Shift.AFTER))
    private void sscextras$headCamera(BlockView world, Entity entity, boolean thirdPerson,
            boolean inverseView, float tickDelta, CallbackInfo ci) {
        setPos(DrakeView.atHead(entity, tickDelta, getPos()));
        if (!thirdPerson && entity instanceof net.minecraft.entity.player.PlayerEntity player
                && sscextras.drake.DrakeSoulbinding.restrained(player))
            setPos(getPos().add(net.minecraft.util.math.MathHelper.sin((player.age + tickDelta) * 2.8f) * .012, 0, 0));
    }
}
