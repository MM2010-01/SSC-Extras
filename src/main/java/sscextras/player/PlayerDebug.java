package sscextras.player;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import sscextras.trainer.QuadrupedalTrainer;

/** Hidden developer shortcuts; the existing player owners perform every mutation. */
public final class PlayerDebug {
    public static final Identifier ADJUST = new Identifier("ssc-extras", "debug_player_adjust");
    private PlayerDebug() { }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ADJUST, (server, player, handler, buffer, sender) -> {
            if (buffer.readableBytes() != 1) return;
            int action = buffer.readUnsignedByte();
            server.execute(() -> adjust(player, action));
        });
    }
    public static boolean allowed(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2) || player.getServer().isSingleplayer()
                && player.getServer().isHost(player.getGameProfile());
    }
    public static boolean adjust(ServerPlayerEntity player, int action) {
        if (!allowed(player) || !player.isAlive() || action < 0 || action > 3) return false;
        if (action < 2) PlayerMind.setPoints(player, PlayerMind.points(player) + (action == 0 ? 10 : -10));
        else QuadrupedalTrainer.adjustTraining(player, (action == 2 ? -1 : 1) * (QuadrupedalTrainer.MAX_PROGRESS / 20));
        return true;
    }
}
