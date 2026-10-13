package sscextras.player;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import sscextras.drake.DrakeOutpostOwnership;
import sscextras.guardian.RuneboundGuardian;

public final class PlayerAspects {
    public static final Identifier RULES_PACKET = new Identifier("ssc-extras", "mental_requirements");
    private PlayerAspects() { }
    public static void register() {
        MentalRequirements.register();
        MentalEnergy.register();
        PlayerNames.register(new Identifier("ssc-extras", "guardian"), 300, player -> {
            if (RuneboundGuardian.stage(player) != 3) return null;
            String name = RuneboundGuardian.state(player).guardianName;
            return !name.isBlank() ? Text.literal(name) : null;
        });
        PlayerNames.register(new Identifier("ssc-extras", "mount"), 200, player -> {
            String name = ((DrakeOutpostOwnership.Display)player).sscExtras$mountName();
            return name.isBlank() ? null : Text.literal(name).formatted(net.minecraft.util.Formatting.ITALIC);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sync(handler.player); PlayerMind.refresh(handler.player); PlayerNames.refresh(handler.player);
        });
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, manager, success) -> {
            if (success) server.getPlayerManager().getPlayerList().forEach(PlayerAspects::sync);
        });
    }
    private static void sync(ServerPlayerEntity player) {
        var buffer = PacketByteBufs.create(); buffer.writeString(MentalRequirements.encode(), 262144);
        ServerPlayNetworking.send(player, RULES_PACKET, buffer);
    }
}
