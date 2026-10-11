package sscextras.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.guardian.*;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class GuardianNetworkMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method = "onPlayerMove", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void sscExtras$cuffsBoundary(net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket packet, CallbackInfo ci) {
        var next = new net.minecraft.util.math.Vec3d(packet.getX(player.getX()), packet.getY(player.getY()), packet.getZ(player.getZ()));
        if (GuardianEquipment.outside(player, player.getWorld().getRegistryKey(), next)) {
            player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
            ci.cancel();
        }
    }
    @Inject(method = {"onPlayerMove", "onPlayerInput", "onClientCommand", "onSpectatorTeleport", "onUpdatePlayerAbilities"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void sscExtras$guardianControlsMovement(CallbackInfo ci) {
        if (GuardianCombat.locked(player) || RuneboundGuardian.state(player).protecting) ci.cancel();
    }
    @Inject(method = {"onPlayerAction", "onPlayerInteractBlock", "onPlayerInteractItem", "onPlayerInteractEntity", "onClickSlot", "onHandSwing"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void sscExtras$coreCannotUseBody(CallbackInfo ci) {
        if (GuardianCombat.locked(player)) { player.currentScreenHandler.syncState(); ci.cancel(); }
    }
    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;playerTick()V", shift = At.Shift.AFTER))
    private void sscExtras$guardianPosition(CallbackInfo ci) {
        if (RuneboundGuardian.state(player).protecting || GuardianCombat.locked(player)) ((ServerPlayNetworkHandler)(Object)this).syncWithPlayerPosition();
    }
}
