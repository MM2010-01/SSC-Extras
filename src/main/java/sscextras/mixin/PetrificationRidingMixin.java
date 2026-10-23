package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.RemoveEntityStatusEffectS2CPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.petrification.Petrification;

@Mixin(ServerPlayerEntity.class)
public abstract class PetrificationRidingMixin {
    @WrapOperation(method = "stopRiding", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void petrificationKeepMountEffect(ServerPlayNetworkHandler handler, Packet<?> packet, Operation<Void> original) {
        // This native loop removes the rider's display of effects still active on the mount, not real cures.
        if (!(packet instanceof RemoveEntityStatusEffectS2CPacket removal) || removal.getEffectType() != Petrification.EFFECT)
            original.call(handler, packet);
    }
}
