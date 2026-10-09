package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.*;

@Mixin(ItemStack.class)
public abstract class GuardianConsumeMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void sscExtras$eatStone(World world, PlayerEntity player, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        ItemStack stack = (ItemStack)(Object)this;
        if (!GuardianCombat.canUse(player, stack)) { cir.setReturnValue(TypedActionResult.fail(stack)); return; }
        if (GuardianCombat.stone(player, stack) && player.getHealth() < player.getMaxHealth()) {
            player.setCurrentHand(hand); cir.setReturnValue(TypedActionResult.consume(stack));
        }
    }
    @Inject(method = "getMaxUseTime", at = @At("HEAD"), cancellable = true)
    private void sscExtras$stoneChewingTime(CallbackInfoReturnable<Integer> cir) {
        if (((ItemStack)(Object)this).isIn(RuneboundGuardian.STONES)) cir.setReturnValue(32);
    }
    @Inject(method = "getUseAction", at = @At("HEAD"), cancellable = true)
    private void sscExtras$stoneChewing(CallbackInfoReturnable<UseAction> cir) {
        if (((ItemStack)(Object)this).isIn(RuneboundGuardian.STONES)) cir.setReturnValue(UseAction.EAT);
    }
    @Inject(method = "finishUsing", at = @At("HEAD"), cancellable = true)
    private void sscExtras$stoneHealing(World world, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack stack = (ItemStack)(Object)this;
        if (!(entity instanceof PlayerEntity player) || !GuardianCombat.stone(player, stack)) return;
        if (!world.isClient) { player.heal(1); if (!player.isCreative()) stack.decrement(1); }
        cir.setReturnValue(stack);
    }
}
