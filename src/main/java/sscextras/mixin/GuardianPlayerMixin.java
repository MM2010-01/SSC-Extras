package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.*;

@Mixin(PlayerEntity.class)
public abstract class GuardianPlayerMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void sscExtras$guardianAttack(Entity target, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (!GuardianCombat.canAttack(player, target)) { ci.cancel(); return; }
        if (!player.getWorld().isClient && RuneboundGuardian.construct(player)) RuneboundGuardian.state(player).spend(.05);
    }
    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void sscExtras$guardianTools(BlockState block, CallbackInfoReturnable<Float> cir) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        ItemStack stack = player.getInventory().main.get(player.getInventory().selectedSlot);
        cir.setReturnValue(GuardianCombat.blockedTool(player, stack) ? 0
                : (float)(cir.getReturnValue() * GuardianCombat.toolEfficiency(player, stack)));
    }
    @Inject(method = "canHarvest", at = @At("HEAD"), cancellable = true)
    private void sscExtras$guardianHarvest(BlockState block, CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (GuardianCombat.blockedTool(player, player.getInventory().main.get(player.getInventory().selectedSlot))) cir.setReturnValue(false);
    }
    @Inject(method = "jump", at = @At("TAIL"))
    private void sscExtras$guardianJump(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (!player.getWorld().isClient && RuneboundGuardian.construct(player)) RuneboundGuardian.state(player).spend(.025);
    }
    @WrapOperation(method = "eatFood", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/HungerManager;eat(Lnet/minecraft/item/Item;Lnet/minecraft/item/ItemStack;)V"))
    private void sscExtras$guardianFood(HungerManager hunger, Item item, ItemStack stack, Operation<Void> original) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        int stage = RuneboundGuardian.stage(player);
        if (stage < 1) { original.call(hunger, item, stack); return; }
        if (stage >= 2 || item.getFoodComponent() == null) return;
        var state = RuneboundGuardian.state(player);
        var food = item.getFoodComponent();
        state.foodRemainder += food.getHunger() * .5;
        int amount = (int)state.foodRemainder; state.foodRemainder -= amount;
        float saturation = hunger.getSaturationLevel();
        hunger.add(amount, 0);
        hunger.setSaturationLevel(Math.min(hunger.getFoodLevel(), saturation + food.getHunger() * food.getSaturationModifier()));
    }
}
