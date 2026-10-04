package sscextras.client;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.PlayerInstinctComponent;
import sscextras.CreatureInstinct;
import sscextras.InstinctTarget;
import sscextras.cuffs.MetalCuffs;

public final class InstinctGainFeedback {
    private PlayerInstinctComponent previous;
    private PlayerFormBase previousForm;
    private float previousValue;
    private double previousBlockedGain;
    private int previousTick, until;
    private float rate, cooldown;
    private boolean suppressed;

    public void update(PlayerEntity player, PlayerInstinctComponent instinct) {
        var form = FormAbilityManager.getForm(player);
        suppressed = MetalCuffs.isSuppressing(player);
        cooldown = CreatureInstinct.cooldownRate(player, instinct);
        int tick = player.age;
        double blockedGain = ((InstinctTarget) instinct).sscExtras$getBlockedGain();
        float blockedChange = (float) Math.max(0, blockedGain - previousBlockedGain);
        float change = instinct.instinctValue - previousValue;
        if (blockedChange > 0) change = Math.max(0, change) + blockedChange;
        if (previous != instinct || previousForm != form || tick < previousTick || blockedGain < previousBlockedGain || change < -0.001f) {
            rate = 0;
            until = tick;
        } else {
            if (tick >= until) rate = 0;
            float extra = change - Math.max(0, instinct.currentInstinctRate) * Math.max(1, tick - previousTick);
            if (extra > 0.001f) {
                rate = Math.max(rate, Math.max(0, instinct.currentInstinctRate) + extra / 20);
                until = tick + 20;
            }
        }
        previous = instinct;
        previousForm = form;
        previousValue = instinct.instinctValue;
        previousBlockedGain = blockedGain;
        previousTick = tick;
    }

    public boolean increasing(PlayerInstinctComponent instinct) {
        return rate > 0 || instinct.isInstinctIncreasing || suppressed && instinct.currentInstinctRate > 0;
    }
    public boolean decreasing(PlayerInstinctComponent instinct) {
        return rate <= 0 && instinct.isInstinctDecreasing && instinct.currentInstinctRate - cooldown < -0.000001f;
    }
    public float rate(PlayerInstinctComponent instinct) { return Math.max(rate, instinct.currentInstinctRate); }
}
