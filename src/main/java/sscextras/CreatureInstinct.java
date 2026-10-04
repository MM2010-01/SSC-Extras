package sscextras;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.PlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.RegPlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect;

public final class CreatureInstinct {
    private CreatureInstinct() { }

    public static boolean add(PlayerEntity player, BaseTransformativeStatusEffect effect) {
        return add(player, effect, SscExtras.instinctPerHit());
    }

    public static boolean add(PlayerEntity player, BaseTransformativeStatusEffect effect, float amount) {
        if (!(player instanceof ServerPlayerEntity) || !player.isAlive()
                || player.isCreative() || player.isSpectator() || effect == null
                || TransformManager.getPlayerTransformData(player).isTransforming
                || (CursedMoon.isCursedMoon(player.getWorld()) && CursedMoon.isNight(player.getWorld()))) {
            return false;
        }

        PlayerFormBase current = FormAbilityManager.getForm(player);
        PlayerFormBase target = effect.getToForm(player);
        if (target == null || target.getIndex() < 0 || target.getGroup() == null) {
            return false;
        }
        boolean original = RegPlayerForms.ORIGINAL_SHIFTER.equals(current);
        if (!original && (current.getIndex() < 0 || current.getIndex() >= 2
                || current.getGroup() == null
                || !current.getGroup().GroupID.equals(target.getGroup().GroupID))) {
            return false;
        }

        PlayerInstinctComponent instinct = RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player);
        InstinctTarget state = (InstinctTarget) instinct;
        if (original) {
            Identifier previous = state.sscExtras$getTarget();
            // A new species starts its own buildup instead of inheriting another creature's progress.
            if (previous != null && !previous.equals(target.FormID)) {
                instinct.instinctValue = 0;
            }
            state.sscExtras$setTarget(target.FormID);
        }
        instinct.instinctValue = Math.min(100.0f, instinct.instinctValue + amount);
        RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.sync(player);
        return true;
    }

    public static void clearTarget(PlayerEntity player) {
        ((InstinctTarget) RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player)).sscExtras$setTarget(null);
    }

    public static PlayerFormBase getTarget(PlayerEntity player) {
        Identifier id = ((InstinctTarget) RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player)).sscExtras$getTarget();
        return id == null ? null : RegPlayerForms.playerForms.get(id);
    }
}
