package sscextras.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class StatueCrateNetworkMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method = {"onPlayerInteractItem", "onClickSlot", "onCreativeInventoryAction", "onSpectatorTeleport", "onUpdateSelectedSlot"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V", shift = At.Shift.AFTER), cancellable = true)
    private void statueCrateContained(CallbackInfo ci) {
        if (sscextras.statue.StatueCrates.packed(player)) ci.cancel();
    }
}
