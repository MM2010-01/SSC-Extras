package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.effigy.InfusionHand;
import sscextras.effigy.Infusions;

@Mixin(PlayerEntity.class)
public abstract class InfusionPlayerMixin {
    @Redirect(method = "updateTurtleHelmet", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getEquippedStack(Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack sscExtras$turtleHelmet(PlayerEntity player, EquipmentSlot slot) {
        return Infusions.enchantmentEquipment(player, slot);
    }

    @WrapMethod(method = "attack")
    private void sscExtras$infusedAttack(Entity target, Operation<Void> original) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        ItemStack stack = Infusions.weapon(player);
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

    @WrapMethod(method = "canHarvest")
    private boolean sscExtras$infusedHarvest(BlockState state, Operation<Boolean> original) {
        PlayerEntity player = (PlayerEntity)(Object)this;
        ItemStack tool = Infusions.tool(player, state);
        if (tool.isEmpty()) return original.call(state);
        try (var ignored = new InfusionHand(player, tool)) { return original.call(state); }
    }

    @Inject(method = "getEquippedStack", at = @At("RETURN"), cancellable = true)
    private void sscExtras$actionHand(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == EquipmentSlot.MAINHAND && cir.getReturnValue().isEmpty()) {
            ItemStack infused = InfusionHand.get((PlayerEntity)(Object)this);
            if (!infused.isEmpty()) cir.setReturnValue(infused);
        }
    }

    @Inject(method = "getArmorItems", at = @At("RETURN"), cancellable = true)
    private void sscExtras$armorEnchantments(CallbackInfoReturnable<Iterable<ItemStack>> cir) {
        cir.setReturnValue(Infusions.armor((PlayerEntity)(Object)this, cir.getReturnValue()));
    }

    @Inject(method = "damageArmor", at = @At("TAIL"))
    private void sscExtras$armorWear(DamageSource source, float amount, CallbackInfo ci) {
        Infusions.damageArmor((PlayerEntity)(Object)this, source, amount, false);
    }

    @Inject(method = "damageHelmet", at = @At("TAIL"))
    private void sscExtras$helmetWear(DamageSource source, float amount, CallbackInfo ci) {
        Infusions.damageArmor((PlayerEntity)(Object)this, source, amount, true);
    }

}
