package sscextras.mixin;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import sscextras.effigy.Infusions;

@Mixin(LivingEntity.class)
public abstract class InfusionArmorEffectsMixin {
    @Redirect(method = {"damage", "canFreeze", "addSoulSpeedBoostIfNeeded"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;getEquippedStack(Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack sscExtras$armorEffects(LivingEntity entity, EquipmentSlot slot) {
        return Infusions.enchantmentEquipment(entity, slot);
    }
}
