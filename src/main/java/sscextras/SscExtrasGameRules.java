package sscextras;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;

public final class SscExtrasGameRules {
    public static final Identifier TRANSFORMATION_BLACKOUT_SYNC = new Identifier("ssc-extras", "transformation_blackout");
    public static final GameRules.Key<GameRules.BooleanRule> COLLAR_AUTO_EQUIP_CURSED = GameRuleRegistry.register(
            "CursedCollarAutoEquipForCursedPlayers", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
    public static final GameRules.Key<GameRules.BooleanRule> COLLAR_AUTO_EQUIP_UNCURSED = GameRuleRegistry.register(
            "CursedCollarAutoEquipForUncursedPlayers", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(false));
    public static final GameRules.Key<GameRules.BooleanRule> PILLAGER_RECRUIT_DRAKE = GameRuleRegistry.register(
            "PillagerMountRecruitingForDrake", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(true));
    public static final GameRules.Key<GameRules.BooleanRule> PILLAGER_RECRUIT_UNCURSED = GameRuleRegistry.register(
            "PillagerMountRecruitingForUncursed", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(false));
    public static final GameRules.Key<GameRules.BooleanRule> REMOVE_BLACKOUT_DURING_TRANSFORMATION = GameRuleRegistry.register(
            "RemoveBlackoutDuringTransformation", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true,
                    (server, rule) -> server.getPlayerManager().getPlayerList().forEach(player -> syncBlackoutRule(player, rule.get()))));

    private SscExtrasGameRules() { }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncBlackoutRule(handler.player,
                handler.player.getWorld().getGameRules().getBoolean(REMOVE_BLACKOUT_DURING_TRANSFORMATION)));
    }

    private static void syncBlackoutRule(ServerPlayerEntity player, boolean removeBlackout) {
        var packet = PacketByteBufs.create();
        packet.writeBoolean(removeBlackout);
        ServerPlayNetworking.send(player, TRANSFORMATION_BLACKOUT_SYNC, packet);
    }
}
