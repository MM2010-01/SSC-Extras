package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import sscextras.drake.DrakeLeashing;

@Mixin(Entity.class)
public abstract class DrakeStepHeightMixin {
    // Forge stores the final height after Pehkui and Apothic attribute changes.
    @ModifyVariable(method = "adjustMovementForCollisions", at = @At("STORE"), ordinal = 0, require = 0)
    private float sscExtras$collisionStepHeight(float height) {
        if ((Object)this instanceof sscextras.drake.StableDrakeEntity) return sscextras.drake.QuadrupedMovement.STEP_HEIGHT;
        if ((Object)this instanceof PlayerEntity player) {
            height = DrakeLeashing.stepHeight(player, height);
            return sscextras.guardian.RuneboundGuardian.stage(player) >= 2 ? Math.max(1.6f, height) : height;
        }
        return height;
    }
}
