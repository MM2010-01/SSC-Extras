package sscextras.mixin;

import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.collar.TamingCollar;

@Mixin(BlockItem.class)
public abstract class TamingCollarPlacementMixin {
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"), cancellable = true)
    private void sscExtras$preventPlacement(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (context.getPlayer() != null && TamingCollar.restricted(context.getPlayer())) cir.setReturnValue(ActionResult.FAIL);
    }
}
