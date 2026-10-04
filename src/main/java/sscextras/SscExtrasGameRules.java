package sscextras;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;

public final class SscExtrasGameRules {
    public static final GameRules.Key<GameRules.BooleanRule> COLLAR_AUTO_EQUIP_CURSED = GameRuleRegistry.register(
            "CursedCollarAutoEquipForCursedPlayers", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(true));
    public static final GameRules.Key<GameRules.BooleanRule> COLLAR_AUTO_EQUIP_UNCURSED = GameRuleRegistry.register(
            "CursedCollarAutoEquipForUncursedPlayers", GameRules.Category.PLAYER, GameRuleFactory.createBooleanRule(false));
    public static final GameRules.Key<GameRules.BooleanRule> PILLAGER_RECRUIT_DRAKE = GameRuleRegistry.register(
            "PillagerMountRecruitingForDrake", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(true));
    public static final GameRules.Key<GameRules.BooleanRule> PILLAGER_RECRUIT_UNCURSED = GameRuleRegistry.register(
            "PillagerMountRecruitingForUncursed", GameRules.Category.MOBS, GameRuleFactory.createBooleanRule(false));

    private SscExtrasGameRules() { }

    public static void register() { }
}
