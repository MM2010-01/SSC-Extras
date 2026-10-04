package sscextras.mixin;

import net.minecraft.block.PowderSnowBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import sscextras.effigy.Infusions;

@Mixin(PowderSnowBlock.class)
public abstract class InfusionPowderSnowMixin {
    @Redirect(method = "canWalkOnPowderSnow", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;getEquippedStack(Lnet/minecraft/entity/EquipmentSlot;)Lnet/minecraft/item/ItemStack;"))
    private static ItemStack sscExtras$leatherBoots(LivingEntity entity, EquipmentSlot slot) {
        return Infusions.enchantmentEquipment(entity, slot);
    }
}
