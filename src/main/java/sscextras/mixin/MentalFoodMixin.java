package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.player.MentalProgression;

@Mixin(PlayerEntity.class)
public abstract class MentalFoodMixin {
    @Inject(method = "eatFood", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;eatFood(Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;"))
    private void sscExtras$rawMeat(World world, ItemStack food, CallbackInfoReturnable<ItemStack> cir) {
        if ((Object)this instanceof ServerPlayerEntity player) MentalProgression.ate(player, food);
    }
}
