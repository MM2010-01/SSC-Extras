package sscextras.client;

import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.PlayerInstinctComponent;

public final class InstinctGainFeedback {
    private PlayerInstinctComponent previous;
    private PlayerFormBase previousForm;
    private float previousValue;
    private int previousTick, until;
    private float rate;

    public void update(PlayerEntity player, PlayerInstinctComponent instinct) {
        var form = FormAbilityManager.getForm(player);
        int tick = player.age;
        float change = instinct.instinctValue - previousValue;
        if (previous != instinct || previousForm != form || tick < previousTick || change < -0.001f) {
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
        previousTick = tick;
    }

    public boolean increasing(PlayerInstinctComponent instinct) { return rate > 0 || instinct.isInstinctIncreasing; }
    public boolean decreasing(PlayerInstinctComponent instinct) { return rate <= 0 && instinct.isInstinctDecreasing; }
    public float rate(PlayerInstinctComponent instinct) { return Math.max(rate, instinct.currentInstinctRate); }
}
