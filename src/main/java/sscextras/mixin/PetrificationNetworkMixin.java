package sscextras.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.petrification.Petrification;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class PetrificationNetworkMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method = {"onPlayerInput", "onClientCommand", "onUpdatePlayerAbilities", "onHandSwing", "onPlayerInteractEntity", "onPlayerInteractBlock"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void petrificationStatue(CallbackInfo ci) {
        if (Petrification.frozen(player)) ci.cancel();
    }
    @Inject(method = "onPlayerMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void petrificationStill(CallbackInfo ci) {
        if (Petrification.frozen(player) && !Petrification.stone(player)) ci.cancel();
    }
    @ModifyVariable(method = "onPlayerMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), argsOnly = true)
    private net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket petrificationFalling(net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket packet) {
        return Petrification.stone(player) ? new net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround(
                player.getX(), Math.min(player.getY(), packet.getY(player.getY())), player.getZ(), packet.isOnGround()) : packet;
    }
    @Inject(method = "onPlayerAction", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void petrificationNoMining(net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (Petrification.frozen(player) && packet.getAction() != net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.RELEASE_USE_ITEM) ci.cancel();
    }
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;playerTick()V", shift = At.Shift.AFTER))
    private void petrificationSyncPosition(CallbackInfo ci) {
        if (Petrification.frozen(player)) ((ServerPlayNetworkHandler)(Object)this).syncWithPlayerPosition();
    }
}
