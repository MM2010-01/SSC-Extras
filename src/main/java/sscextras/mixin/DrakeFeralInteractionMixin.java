package sscextras.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeFeralization;
import sscextras.drake.DrakeShoes;
import sscextras.drake.DrakeSoulbinding;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class DrakeFeralInteractionMixin {
    @Shadow protected ServerPlayerEntity player;
    @Inject(method = "processBlockBreakingAction", at = @At("HEAD"), cancellable = true)
    private void sscExtras$noMining(CallbackInfo ci) {
        if (DrakeFeralization.restricted(player) || DrakeShoes.restricted(player) || DrakeSoulbinding.restrained(player)) ci.cancel();
    }
    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void sscExtras$noBreaking(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (DrakeFeralization.restricted(player) || DrakeShoes.restricted(player) || DrakeSoulbinding.restrained(player)) cir.setReturnValue(false);
    }
    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void sscExtras$hayOnly(ServerPlayerEntity player, World world, ItemStack stack, Hand hand,
                                   net.minecraft.util.hit.BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (DrakeSoulbinding.restrained(player)) { cir.setReturnValue(ActionResult.FAIL); return; }
        var result = DrakeShoes.useBlock(player, hit.getBlockPos());
        if (result == ActionResult.PASS) result = DrakeFeralization.useBlock(player, hit.getBlockPos());
        if (result != ActionResult.PASS) cir.setReturnValue(result);
    }
    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void sscExtras$rawOnly(ServerPlayerEntity player, World world, ItemStack stack, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (DrakeSoulbinding.restrained(player) || !DrakeShoes.canUse(player, hand)
                || DrakeFeralization.restricted(player) && (DrakeFeralization.controlled(player) || !DrakeFeralization.rawFood(stack)))
            cir.setReturnValue(ActionResult.FAIL);
    }
}
