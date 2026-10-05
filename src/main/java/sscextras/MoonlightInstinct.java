package sscextras;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import sscextras.drake.DrakeSoulbinding;

public final class MoonlightInstinct {
    private MoonlightInstinct() { }

    public static float rate(PlayerEntity player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || DrakeSoulbinding.bound(player)
                || InstinctTicker.isPausing || TransformManager.getPlayerTransformData(player).isTransforming) return 0;
        var world = player.getWorld();
        if (!world.getDimension().hasSkyLight() || world.getDimension().hasCeiling()
                || !CursedMoon.isCursedMoon(world) || !CursedMoon.isNight(world)) return 0;
        var form = FormAbilityManager.getForm(player);
        if (form == RegPlayerForms.ORIGINAL_BEFORE_ENABLE
                || (form.getIndex() >= 2 && CreatureInstinct.permanentTarget(form) == null)
                || !world.isSkyVisible(player.getBlockPos())) return 0;
        return 0.05f;
    }
}
