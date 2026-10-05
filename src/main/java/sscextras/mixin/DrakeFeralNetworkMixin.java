package sscextras.mixin;

import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeFeralization;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class DrakeFeralNetworkMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;playerTick()V", shift = At.Shift.AFTER))
    private void sscExtras$keepServerMovement(CallbackInfo ci) {
        if (DrakeFeralization.controlled(player)) ((ServerPlayNetworkHandler)(Object)this).syncWithPlayerPosition();
    }

    @Inject(method = {"onPlayerMove", "onPlayerInput", "onClientCommand"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void sscExtras$aiOwnsBody(CallbackInfo ci) {
        if (DrakeFeralization.controlled(player) || sscextras.drake.DrakeSoulbinding.restrained(player)) ci.cancel();
    }

    @Inject(method = "onClickSlot", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void sscExtras$foodInventory(ClickSlotC2SPacket packet, CallbackInfo ci) {
        if (!DrakeFeralization.allowInventoryClick(player, packet.getSlot(), packet.getButton(), packet.getActionType())) {
            player.currentScreenHandler.syncState(); ci.cancel();
        }
    }

    @Inject(method = "onPlayerAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void sscExtras$noItemActions(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (DrakeFeralization.restricted(player) && (DrakeFeralization.controlled(player)
                || switch (packet.getAction()) {
                    case START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK, RELEASE_USE_ITEM -> false;
                    default -> true;
                })) {
            player.currentScreenHandler.syncState(); ci.cancel();
        }
    }
}
