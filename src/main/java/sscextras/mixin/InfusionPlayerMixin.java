package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.StackReference;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.effigy.InfusionHand;
import sscextras.effigy.Infusions;
import sscextras.player.EmptySlot;

@Mixin(PlayerEntity.class)
public abstract class InfusionPlayerMixin {
    @Redirect(method = "updateTurtleHelmet", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getEquippedStack(Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack sscExtras$turtleHelmet(PlayerEntity player, EquipmentSlot slot) {
        return Infusions.enchantmentEquipment(player, slot);
    }

    @WrapMethod(method = "attack")
    private void sscExtras$infusedAttack(Entity target, Operation<Void> original) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        ItemStack stack = Infusions.combatWeapon(player);
        if (stack.isEmpty()) { original.call(target); return; }
        Infusions.inventory(player).refreshAttributes();
        try (var ignored = new InfusionHand(player, stack)) {
            original.call(target);
        } finally {
            Infusions.changed(player);
        }
    }

    @WrapMethod(method = "getBlockBreakingSpeed")
    private float sscExtras$infusedMiningSpeed(BlockState state, Operation<Float> original) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        ItemStack tool = Infusions.tool(player, state);
        if (tool.isEmpty()) return original.call(state);
        try (var ignored = new InfusionHand(player, tool)) { return original.call(state); }
    }

    // Forge's block-breaking path calls this positional overload directly.
    @WrapMethod(method = "getDigSpeed", remap = false, require = 0)
    private float sscExtras$infusedForgeMiningSpeed(BlockState state, BlockPos pos, Operation<Float> original) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (!InfusionHand.get(player).isEmpty()) return original.call(state, pos);
        ItemStack tool = Infusions.tool(player, state);
        if (tool.isEmpty()) return original.call(state, pos);
        try (var ignored = new InfusionHand(player, tool)) { return original.call(state, pos); }
    }

    @WrapMethod(method = "canHarvest")
    private boolean sscExtras$infusedHarvest(BlockState state, Operation<Boolean> original) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        ItemStack tool = Infusions.tool(player, state);
        if (tool.isEmpty()) return original.call(state);
        try (var ignored = new InfusionHand(player, tool)) { return original.call(state); }
    }

    @Inject(method = "getEquippedStack", at = @At("RETURN"), cancellable = true)
    private void sscExtras$actionHand(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (EmptySlot.physicalHands((PlayerEntity)(Object)this)) return;
        if (slot.getType() == EquipmentSlot.Type.HAND) cir.setReturnValue(EmptySlot.held(cir.getReturnValue()));
        if (slot == EquipmentSlot.MAINHAND && cir.getReturnValue().isEmpty()) {
            ItemStack infused = InfusionHand.get((PlayerEntity)(Object)this);
            if (!infused.isEmpty()) cir.setReturnValue(infused);
        }
    }

    @Inject(method = "equipStack", at = @At("HEAD"), cancellable = true)
    private void sscExtras$receiveIntoPlaceholder(EquipmentSlot slot, ItemStack incoming,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        if (slot.getType() != EquipmentSlot.Type.HAND || EmptySlot.physicalHands(player)) return;
        var inventory = player.getInventory();
        ItemStack physical = slot == EquipmentSlot.MAINHAND ? inventory.main.get(inventory.selectedSlot) : inventory.offHand.get(0);
        if (physical == incoming || !EmptySlot.isPlaceholder(physical)) return;
        // An empty-hand interaction (e.g. taking armor from a stand) cannot overwrite the reserved slot.
        if (!incoming.isEmpty() && !player.getWorld().isClient) inventory.offerOrDrop(incoming);
        ci.cancel();
    }

    @Inject(method = "getStackReference", at = @At("HEAD"), cancellable = true)
    private void sscExtras$physicalInventorySlot(int mappedIndex, CallbackInfoReturnable<StackReference> cir) {
        if (mappedIndex != 98 && mappedIndex != 99) return; // weapon.mainhand / offhand
        PlayerEntity player = (PlayerEntity)(Object)this;
        int slot = mappedIndex == 98 ? player.getInventory().selectedSlot : 40;
        if (EmptySlot.isPlaceholder(player.getInventory().getStack(slot))
                || mappedIndex == 98 && !InfusionHand.get(player).isEmpty()) {
            cir.setReturnValue(StackReference.of(player.getInventory(), slot));
        }
    }

    @Inject(method = "getArmorItems", at = @At("RETURN"), cancellable = true)
    private void sscExtras$armorEnchantments(CallbackInfoReturnable<Iterable<ItemStack>> cir) {
        cir.setReturnValue(Infusions.armor((PlayerEntity)(Object)this, cir.getReturnValue()));
    }

}
