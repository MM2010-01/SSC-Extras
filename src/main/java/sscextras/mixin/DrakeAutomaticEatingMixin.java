package sscextras.mixin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.drake.EarthenDrake;

@Mixin(MinecraftClient.class)
public abstract class DrakeAutomaticEatingMixin {
    @WrapWithCondition(method = "handleInputEvents", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;stopUsingItem(Lnet/minecraft/entity/player/PlayerEntity;)V"))
    private boolean sscExtras$finishAutomaticMeal(ClientPlayerInteractionManager manager, PlayerEntity player) {
        return player.getActiveHand() != Hand.MAIN_HAND || !EarthenDrake.wantsMouthMeat(player);
    }
}
