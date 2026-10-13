package sscextras.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeFeralization;

@Mixin(ItemStack.class)
public abstract class DrakeFeralItemUseMixin {
    @Inject(method = "useOnBlock", at = @At("HEAD"), cancellable = true)
    private void sscExtras$blockItemsOnly(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        var player = context.getPlayer();
        if (player != null && (!DrakeFeralization.canUse(player, (ItemStack)(Object)this)
                || !sscextras.player.PlayerMind.allow(player, sscextras.player.MentalContext.item(
                    ((ItemStack)(Object)this).getItem() instanceof net.minecraft.item.BlockItem ? sscextras.player.MentalContext.Action.PLACE : sscextras.player.MentalContext.Action.USE,
                    (ItemStack)(Object)this))
                || sscextras.drake.FormItemRestrictions.prevents(player, (ItemStack)(Object)this))) cir.setReturnValue(ActionResult.FAIL);
    }
}
