package sscextras.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.drake.EarthenDrake;

@Mixin(LivingEntity.class)
public abstract class DrakeEatingMixin {
    @ModifyExpressionValue(method = {"setCurrentHand", "onTrackedDataSet"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getMaxUseTime()I"))
    private int sscExtras$eatFaster(int original) {
        var entity = (LivingEntity)(Object)this;
        return entity instanceof PlayerEntity player ? EarthenDrake.eatingDuration(player, entity.getActiveItem(), original) : original;
    }
}
