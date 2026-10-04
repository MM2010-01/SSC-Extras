package sscextras;

import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.cursed_moon.CursedMoon;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.RegPlayerInstinctComponent;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;

public final class MoonlightInstinct {
    private MoonlightInstinct() { }

    public static void tick(ServerPlayerEntity player) {
        if (player.age % 20 != 0 || !player.isAlive() || player.isCreative() || player.isSpectator()
                || InstinctTicker.isPausing || TransformManager.getPlayerTransformData(player).isTransforming) return;
        var world = player.getServerWorld();
        if (!world.getDimension().hasSkyLight() || world.getDimension().hasCeiling()
                || !CursedMoon.isCursedMoon(world) || !CursedMoon.isNight(world)) return;
        var form = FormAbilityManager.getForm(player);
        if (form == RegPlayerForms.ORIGINAL_BEFORE_ENABLE
                || (form.getIndex() >= 2 && CreatureInstinct.permanentTarget(form) == null)
                || !world.isSkyVisible(player.getBlockPos())) return;
        var instinct = RegPlayerInstinctComponent.PLAYER_INSTINCT_COMP.get(player);
        instinct.instinctValue = Math.min(100, instinct.instinctValue + 1);
    }
}
