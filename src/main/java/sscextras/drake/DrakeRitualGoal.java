package sscextras.drake;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.EnumSet;

public final class DrakeRitualGoal extends Goal {
    private final PillagerEntity pillager;
    private ServerPlayerEntity player;
    private int nextPath;

    public DrakeRitualGoal(PillagerEntity pillager) {
        this.pillager = pillager;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override public boolean canStart() {
        player = DrakeSoulbinding.attendee(pillager);
        return player != null && DrakeSoulbinding.available(pillager);
    }

    @Override public boolean shouldContinue() {
        return player != null && player.isAlive() && player == DrakeSoulbinding.attendee(pillager) && DrakeSoulbinding.available(pillager);
    }

    @Override public void start() {
        nextPath = 0;
        pillager.setTarget(null); pillager.clearActiveItem(); pillager.setCharging(false);
    }

    @Override public void tick() {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null) return;
        int index = claim.attendants.indexOf(pillager.getUuid());
        if (index < 0) return;
        var target = DrakeSoulbinding.attendancePosition(claim, index);
        var navigation = (DrakeStableNavigation)pillager.getNavigation();
        navigation.open(claim.gate());
        pillager.getLookControl().lookAt(player, 30, 30);
        if (pillager.squaredDistanceTo(target) <= .5) navigation.stop();
        else if (pillager.age >= nextPath) {
            nextPath = pillager.age + 10;
            navigation.startMovingTo(target.x, target.y, target.z, .8);
        }
    }

    @Override public void stop() { player = null; pillager.getNavigation().stop(); }
}
