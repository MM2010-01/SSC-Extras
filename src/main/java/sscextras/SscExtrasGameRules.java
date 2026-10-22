package sscextras;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import java.util.Set;

public final class SscExtrasGameRules {
    public static final Identifier TRANSFORMATION_BLACKOUT_SYNC = new Identifier("ssc-extras", "transformation_blackout");
    public static final Identifier PERMANENT_PROGRESSION_SYNC = new Identifier("ssc-extras", "permanent_progression");
    public static final Identifier INT_SYSTEM_SYNC = new Identifier("ssc-extras", "int_system");
    private static boolean clientIntSystemEnabled = true;
    private static boolean clientPermanentProgressionUnlocked = true;
    private static Set<Identifier> clientPermanentProgressionExceptions = Set.of();
    public static final GameRules.Key<GameRules.BooleanRule> CURSED_LOOT_AUTO_EQUIP_CURSED = GameRuleRegistry.register(
            "CursedLootAutoEquipForCursedPlayers", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
    public static final GameRules.Key<GameRules.BooleanRule> CURSED_LOOT_AUTO_EQUIP_UNCURSED = GameRuleRegistry.register(
            "CursedLootAutoEquipForUncursedPlayers", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
    public static final GameRules.Key<GameRules.BooleanRule> PILLAGER_RECRUIT_DRAKE = GameRuleRegistry.register(
            "PillagerMountRecruitingForDrake", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(true));
    public static final GameRules.Key<GameRules.BooleanRule> PILLAGER_RECRUIT_UNCURSED = GameRuleRegistry.register(
            "PillagerMountRecruitingForUncursed", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(false));
    public static final GameRules.Key<GameRules.BooleanRule> REMOVE_BLACKOUT_DURING_TRANSFORMATION = GameRuleRegistry.register(
            "RemoveBlackoutDuringTransformation", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true,
                    (server, rule) -> server.getPlayerManager().getPlayerList().forEach(player -> syncBlackoutRule(player, rule.get()))));
    public static final GameRules.Key<GameRules.BooleanRule> UNLOCKED_PERMANENT_FORM_PROGRESSION_FOR_OTHER_FORMS = GameRuleRegistry.register(
            "UnlockedPermanentFormProgressionForOtherForms", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true,
                    (server, rule) -> server.getPlayerManager().getPlayerList().forEach(player -> syncPermanentProgression(player, rule.get()))));
    public static final GameRules.Key<GameRules.BooleanRule> UNLOCKED_PERMANENT_FORM_REVERSION = GameRuleRegistry.register(
            "UnlockedPermanentFormReversion", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
    public static final GameRules.Key<GameRules.BooleanRule> ENABLE_INT_SYSTEM = GameRuleRegistry.register(
            "EnableINTSystem", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true,
                    (server, rule) -> server.getPlayerManager().getPlayerList().forEach(player -> {
                        syncIntSystem(player, rule.get());
                        player.clearActiveItem(); player.closeHandledScreen();
                        sscextras.player.PlayerMind.refresh(player);
                        player.playerScreenHandler.onContentChanged(player.getInventory());
                    })));
    public static final GameRules.Key<GameRules.BooleanRule> INSTINCT_GAIN_INT_DRAIN = GameRuleRegistry.register(
            "InstinctGainINTDrain", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
    public static final GameRules.Key<GameRules.BooleanRule> PERMANENT_FORM_INT_DRAIN = GameRuleRegistry.register(
            "PermanentFormINTDrain", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));

    private SscExtrasGameRules() { }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var rules = handler.player.getWorld().getGameRules();
            syncBlackoutRule(handler.player, rules.getBoolean(REMOVE_BLACKOUT_DURING_TRANSFORMATION));
            syncPermanentProgression(handler.player, rules.getBoolean(UNLOCKED_PERMANENT_FORM_PROGRESSION_FOR_OTHER_FORMS));
            syncIntSystem(handler.player, rules.getBoolean(ENABLE_INT_SYSTEM));
        });
    }

    public static boolean intSystemEnabled(PlayerEntity player) {
        return player.getWorld().isClient ? clientIntSystemEnabled : player.getWorld().getGameRules().getBoolean(ENABLE_INT_SYSTEM);
    }

    public static void setClientIntSystem(boolean enabled) { clientIntSystemEnabled = enabled; }

    private static void syncIntSystem(ServerPlayerEntity player, boolean enabled) {
        var packet = PacketByteBufs.create(); packet.writeBoolean(enabled);
        ServerPlayNetworking.send(player, INT_SYSTEM_SYNC, packet);
    }

    public static boolean permanentProgressionUnlocked(PlayerEntity player, Identifier form) {
        return player.getWorld().isClient
                ? clientPermanentProgressionUnlocked || clientPermanentProgressionExceptions.contains(form)
                : player.getWorld().getGameRules().getBoolean(UNLOCKED_PERMANENT_FORM_PROGRESSION_FOR_OTHER_FORMS)
                        || SscExtrasConfig.permanentFormProgressionExceptions().contains(form);
    }

    public static void setClientPermanentProgression(boolean unlocked, Set<Identifier> exceptions) {
        clientPermanentProgressionUnlocked = unlocked;
        clientPermanentProgressionExceptions = Set.copyOf(exceptions);
    }

    private static void syncPermanentProgression(ServerPlayerEntity player, boolean unlocked) {
        var packet = PacketByteBufs.create();
        packet.writeBoolean(unlocked);
        packet.writeCollection(SscExtrasConfig.permanentFormProgressionExceptions(), (buf, id) -> buf.writeIdentifier(id));
        ServerPlayNetworking.send(player, PERMANENT_PROGRESSION_SYNC, packet);
    }

    private static void syncBlackoutRule(ServerPlayerEntity player, boolean removeBlackout) {
        var packet = PacketByteBufs.create();
        packet.writeBoolean(removeBlackout);
        ServerPlayNetworking.send(player, TRANSFORMATION_BLACKOUT_SYNC, packet);
    }
}
