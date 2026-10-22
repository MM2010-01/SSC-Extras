package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import sscextras.player.EmptySlot;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class EmptySlotNetworkMixin {
    @Shadow public ServerPlayerEntity player;

    @WrapMethod(method = "onPlayerAction")
    private void sscExtras$physicalHandSwap(PlayerActionC2SPacket packet, Operation<Void> original) {
        if (packet.getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
            EmptySlot.withPhysicalHands(player, () -> original.call(packet));
        } else original.call(packet);
    }
}
