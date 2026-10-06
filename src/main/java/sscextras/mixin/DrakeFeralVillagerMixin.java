package sscextras.mixin;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeFeralization;

@Mixin(VillagerEntity.class)
public abstract class DrakeFeralVillagerMixin {
    @Inject(method = "interactMob", at = @At("HEAD"), cancellable = true)
    private void sscExtras$refuseFeralTrade(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        var result = DrakeFeralization.useEntity(player, (VillagerEntity)(Object)this, hand);
        if (result != ActionResult.PASS) cir.setReturnValue(result);
    }
}
