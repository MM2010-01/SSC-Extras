package sscextras.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import sscextras.drake.*;

@Mixin(MinecraftClient.class)
public abstract class DrakeRiderInventoryMixin {
    @Shadow public ClientPlayerEntity player;

    @Redirect(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;hasRidingInventory()Z"))
    private boolean sscExtras$hasRiderChest(ClientPlayerInteractionManager manager) {
        return player.getVehicle() instanceof PlayerEntity drake
                && !DrakeEquipment.equipped(drake, DrakeEquipment.RIDERS_CHEST).isEmpty() || manager.hasRidingInventory();
    }

    @Redirect(method = "handleInputEvents", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;openRidingInventory()V"))
    private void sscExtras$openRiderChest(ClientPlayerEntity rider) {
        if (rider.getVehicle() instanceof PlayerEntity drake && !DrakeEquipment.equipped(drake, DrakeEquipment.RIDERS_CHEST).isEmpty())
            ClientPlayNetworking.send(DrakeRiding.CHEST, PacketByteBufs.create());
        else rider.openRidingInventory();
    }
}
