package sscextras.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import sscextras.petrification.*;

public final class PetrificationMiningClient {
    private static int lastTarget = -1;
    private PetrificationMiningClient() { }
    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> lastTarget = -1);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            int target = client.currentScreen == null && client.options.attackKey.isPressed() && !client.player.isUsingItem()
                    && !Petrification.frozen(client.player) && client.crosshairTarget instanceof EntityHitResult hit
                    && PetrificationMining.mineable(client.player, hit.getEntity()) ? hit.getEntity().getId() : -1;
            if (target != lastTarget || target != -1 && client.player.age % 5 == 0) {
                var packet = PacketByteBufs.create(); packet.writeVarInt(target);
                ClientPlayNetworking.send(PetrificationMining.INTENT, packet);
            }
            if (target != -1) client.player.swingHand(Hand.MAIN_HAND);
            lastTarget = target;
        });
    }
}
