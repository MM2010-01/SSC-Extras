package sscextras.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.drake.DrakeFeralization;

@Mixin(PlayerEntity.class)
public abstract class DrakeFeralPostureMixin {
    @ModifyReturnValue(method = "getDimensions", at = @At("RETURN"))
    private EntityDimensions sscExtras$quadrupedSize(EntityDimensions dimensions, EntityPose pose) {
        return (pose == EntityPose.STANDING || pose == EntityPose.CROUCHING)
                && DrakeFeralization.forcedQuadruped((PlayerEntity)(Object)this)
                ? dimensions.scaled(1, pose == EntityPose.STANDING ? 1.25f / 1.8f : 1.25f / 1.5f) : dimensions;
    }

    @ModifyReturnValue(method = "getActiveEyeHeight", at = @At("RETURN"))
    private float sscExtras$quadrupedEyes(float height, EntityPose pose, EntityDimensions dimensions) {
        return (pose == EntityPose.STANDING || pose == EntityPose.CROUCHING)
                && DrakeFeralization.forcedQuadruped((PlayerEntity)(Object)this) ? height * .62f : height;
    }
}
