package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.onixary.shapeShifterCurseFabric.networking.ModPacketsS2C;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = ModPacketsS2C.class, remap = false)
public abstract class PowerAnimationPacketMixin {
    @WrapMethod(method = "receivePowerAnimationData")
    private static void sscExtras$resolveAnimationAfterRespawn(MinecraftClient client, ClientPlayNetworkHandler handler,
            PacketByteBuf buffer, PacketSender sender, Operation<Void> original) {
        var pending = new PacketByteBuf(buffer.copy());
        client.execute(() -> {
            try {
                var id = pending.readUuid(); pending.readerIndex(0);
                if (client.world != null && client.world.getPlayerByUuid(id) != null)
                    original.call(client, handler, pending, sender);
            } finally { pending.release(); }
        });
    }
}
