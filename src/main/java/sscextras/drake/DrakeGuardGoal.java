package sscextras.drake;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import java.util.EnumSet;

public final class DrakeGuardGoal extends Goal {
    private final PillagerEntity pillager;
    private PlayerEntity following;
    private Path patrol;
    private int nextPatrol, nextPath;

    public DrakeGuardGoal(PillagerEntity pillager) {
        this.pillager = pillager;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    private DrakeStableNavigation navigation() { return (DrakeStableNavigation)pillager.getNavigation(); }

    @Override public boolean canStart() {
        if (pillager.hasVehicle() || pillager.hasActiveRaid() || pillager.getTarget() != null) return false;
        var stable = navigation().stable();
        if (stable == null) return false;
        following = DrakeRoaming.following(pillager);
        if (following != null) return true;
        if (pillager.age < nextPatrol) return false;
        nextPatrol = pillager.age + 40 + pillager.getRandom().nextInt(60);
        var box = stable.getBoundingBox();
        for (int i = 0; i < 8; i++) {
            int x = box.getMinX() - 60 + pillager.getRandom().nextInt(box.getBlockCountX() + 120);
            int z = box.getMinZ() - 60 + pillager.getRandom().nextInt(box.getBlockCountZ() + 120);
            var column = new BlockPos(x, 0, z);
            if (!pillager.getWorld().isChunkLoaded(column) || !DrakeCaptureGoal.near(box, Vec3d.ofCenter(column), DrakeRoaming.RANGE)) continue;
            var pos = pillager.getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
            if (box.contains(pos) || pillager.squaredDistanceTo(Vec3d.ofBottomCenter(pos)) < 16) continue;
            patrol = navigation().findPathTo(pos, 0);
            if (patrol != null && patrol.reachesTarget()) return true;
        }
        return false;
    }

    @Override public void start() {
        pillager.clearActiveItem(); pillager.setCharging(false);
        nextPath = 0;
        if (following == null) navigation().startMovingAlong(patrol, .65);
    }

    @Override public boolean shouldContinue() {
        return !pillager.hasVehicle() && !pillager.hasActiveRaid() && pillager.getTarget() == null
                && (following != null ? following == DrakeRoaming.following(pillager) : !navigation().isIdle());
    }

    @Override public void tick() {
        var spotted = DrakeRoaming.following(pillager);
        if (spotted != null) following = spotted;
        if (following == null) return;
        pillager.getLookControl().lookAt(following, 30, 30);
        if (pillager.squaredDistanceTo(following) < 9) navigation().stop();
        else if (pillager.age >= nextPath) {
            nextPath = pillager.age + 10;
            navigation().startMovingTo(following, 1);
        }
    }

    @Override public void stop() { following = null; patrol = null; navigation().stop(); }
}
