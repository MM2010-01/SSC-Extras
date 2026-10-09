package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.GuardianCombat;

@Mixin(PotionItem.class)
public abstract class GuardianPotionMixin {
    @Inject(method = "finishUsing", at = @At("HEAD"))
    private void sscExtras$consumeMagic(ItemStack stack, World world, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        if (entity instanceof PlayerEntity player) GuardianCombat.potion(player, stack);
    }
}
