package sscextras.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.golem.DrakeGolem;

/** The golem already lies along its body axis; vanilla's upright-body sleep roll does not fit it. */
@Mixin(LivingEntityRenderer.class)
public class DrakeGolemPoseMixin {
    @ModifyExpressionValue(method={"render","setupTransforms"},at=@At(value="INVOKE",
            target="Lnet/minecraft/entity/LivingEntity;isInPose(Lnet/minecraft/entity/EntityPose;)Z"))
    private boolean golemSleepingTransform(boolean sleeping, LivingEntity entity) {
        return sleeping && !(entity instanceof PlayerEntity player && DrakeGolem.is(player));
    }
}
