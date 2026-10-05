package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeFeralization;

@Mixin(LivingEntity.class)
public abstract class DrakeFeralUseMixin {
    @Inject(method = "setCurrentHand", at = @At("HEAD"), cancellable = true)
    private void sscExtras$onlyEatFood(Hand hand, CallbackInfo ci) {
        if ((Object)this instanceof PlayerEntity player && (sscextras.drake.DrakeSoulbinding.restrained(player)
                || !sscextras.drake.DrakeShoes.canUse(player, hand)
                || DrakeFeralization.restricted(player) && !DrakeFeralization.edible(player.getStackInHand(hand)))) ci.cancel();
    }
}
