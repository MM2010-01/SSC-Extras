package sscextras.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.effigy.InfusionHand;
import sscextras.player.EmptySlot;

@Mixin(PlayerInventory.class)
public abstract class InfusionPlayerInventoryMixin {
    @Shadow @Final public PlayerEntity player;

    @Inject(method = "getMainHandStack", at = @At("RETURN"), cancellable = true)
    private void sscExtras$actionHand(CallbackInfoReturnable<ItemStack> cir) {
        if (EmptySlot.physicalHands(player) || !EmptySlot.isBare(cir.getReturnValue())) return;
        ItemStack stack = InfusionHand.get(player);
        cir.setReturnValue(stack);
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "dropSelectedItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerInventory;getMainHandStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack sscExtras$dropPhysicalSlot(PlayerInventory inventory) {
        return inventory.main.get(inventory.selectedSlot);
    }

    @Inject(method = "getBlockBreakingSpeed", at = @At("HEAD"), cancellable = true)
    private void sscExtras$toolSpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        ItemStack stack = InfusionHand.get(player);
        if (!stack.isEmpty()) cir.setReturnValue(stack.getMiningSpeedMultiplier(state));
    }
}
