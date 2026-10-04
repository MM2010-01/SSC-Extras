package sscextras;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.PlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.RegPlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect;

public final class CreatureInstinct {
    public static final float PERMANENT_INSTINCT_COST = 10.0f;

    private CreatureInstinct() { }

    public static boolean add(PlayerEntity player, BaseTransformativeStatusEffect effect) {
        return add(player, effect, SscExtras.instinctPerHit());
    }

    public static boolean add(PlayerEntity player, BaseTransformativeStatusEffect effect, float amount) {
        if (!(player instanceof ServerPlayerEntity) || !player.isAlive()
                || player.isCreative() || player.isSpectator() || effect == null
                || TransformManager.getPlayerTransformData(player).isTransforming) {
            return false;
        }

        PlayerFormBase current = FormAbilityManager.getForm(player);
        PlayerFormBase target = effect.getToForm(player);
        if (target == null || target.getIndex() < 0 || target.getGroup() == null) {
            return false;
        }
        boolean original = RegPlayerForms.ORIGINAL_SHIFTER.equals(current);
        if (!original && (current.getIndex() < 0
                || (current.getIndex() >= 2 && permanentTarget(current) == null)
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
        sscextras.collar.Collars.equipCarried(player);
        instinct.instinctValue = Math.min(100.0f, instinct.instinctValue
                + sscextras.collar.Collars.gain(player, amount) / costMultiplier(current));
        RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.sync(player);
        return true;
    }

    public static void clearTarget(PlayerEntity player) {
        ((InstinctTarget) RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player)).sscExtras$setTarget(null);
    }

    public static PlayerFormBase permanentTarget(PlayerFormBase form) {
        return form.getIndex() == 2 && form.getGroup() != null && form.getGroup().hasForm(3)
                ? form.getGroup().getForm(3) : null;
    }

    public static float costMultiplier(PlayerFormBase form) {
        return permanentTarget(form) != null ? PERMANENT_INSTINCT_COST : 1.0f;
    }

    public static PlayerFormBase getTarget(PlayerEntity player) {
        Identifier id = ((InstinctTarget) RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player)).sscExtras$getTarget();
        return id == null ? null : RegPlayerForms.playerForms.get(id);
    }
}
