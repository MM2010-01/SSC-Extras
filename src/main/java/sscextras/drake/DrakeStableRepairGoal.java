package sscextras.drake;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.Vec3d;
import java.util.EnumSet;

public final class DrakeStableRepairGoal extends Goal {
    private final PillagerEntity guard;
    private DrakeStablePiece stable;
    private BlockBox box;
    private boolean active;
    private BlockPos target;
    private Vec3d approach;
    private int cursor, nextScan, started, nextPath, work;

    public DrakeStableRepairGoal(PillagerEntity guard) {
        this.guard = guard;
        cursor = guard.getRandom().nextInt(8192);
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    boolean active() { return active; }
    private boolean available() {
        if (guard instanceof MountMerchantEntity merchant) return merchant.canRepairStructure();
        return DrakeSoulbinding.available(guard) && DrakeSoulbinding.attendee(guard) == null && DrakeRoaming.following(guard) == null;
    }
    private net.minecraft.block.BlockState expected(BlockPos pos) {
        return guard instanceof MountMerchantEntity merchant ? MountMerchantMaintenance.expected(merchant.site(), pos)
                : DrakeStableMaintenance.expected(box, pos);
    }
    @Override public boolean canStart() {
        if (guard.age < nextScan || !available()) return false;
        nextScan = guard.age + 40;
        if (guard instanceof MountMerchantEntity merchant) box = MountMerchantMaintenance.bounds(merchant.site());
        else {
            stable = ((DrakeStableNavigation)guard.getNavigation()).stable();
            if (stable == null) return false;
            box = stable.getBoundingBox();
        }
        int width = box.getBlockCountX(), depth = box.getBlockCountZ(), volume = width * depth * box.getBlockCountY();
        for (int scanned = 0; scanned < Math.min(256, volume); scanned++) {
            int index = cursor++ % volume;
            var pos = new BlockPos(box.getMinX() + index % width, box.getMinY() + index / (width * depth),
                    box.getMinZ() + index / width % depth);
            if (!guard.getWorld().isChunkLoaded(pos) || DrakeStableMaintenance.matches(guard.getWorld().getBlockState(pos),
                    expected(pos))) continue;
            if (guard instanceof MountMerchantEntity merchant) ((DrakeStableNavigation)guard.getNavigation()).open(merchant.gate());
            var floor = new BlockPos(pos.getX(), box.getMinY() + 1, pos.getZ());
            approach = null;
            for (int radius = 0; radius <= 2 && approach == null; radius++) for (int dx = -radius; dx <= radius && approach == null; dx++)
                for (int dz = -radius; dz <= radius && approach == null; dz++) {
                    var point = floor.add(dx, 0, dz);
                    if (!guard.getWorld().getBlockState(point.down()).isSolidBlock(guard.getWorld(), point.down())) continue;
                    var path = guard.getNavigation().findPathTo(point, 0);
                    if (path != null && path.reachesTarget()) approach = Vec3d.ofBottomCenter(point);
                }
            if (approach != null) { target = pos; return true; }
        }
        return false;
    }

    @Override public void start() {
        active = true;
        started = guard.age; nextPath = work = 0;
        guard.clearActiveItem(); guard.setCharging(false);
    }
    @Override public boolean shouldRunEveryTick() { return true; }
    @Override public boolean shouldContinue() {
        return target != null && guard.age - started < 200 && available()
                && guard.getWorld().isChunkLoaded(target) && !DrakeStableMaintenance.matches(guard.getWorld().getBlockState(target),
                        expected(target));
    }
    @Override public void tick() {
        if (target == null) return;
        guard.getLookControl().lookAt(Vec3d.ofCenter(target));
        if (guard.squaredDistanceTo(approach) > 2.25) {
            if (guard.age >= nextPath) {
                nextPath = guard.age + 20;
                guard.getNavigation().startMovingTo(approach.x, approach.y, approach.z,
                        guard instanceof MountMerchantEntity ? MountMerchantEntity.MOVE_SPEED : .8);
            }
            return;
        }
        guard.getNavigation().stop();
        if (++work % 10 == 0) guard.swingHand(Hand.MAIN_HAND);
        if (work >= 30 && (guard instanceof MountMerchantEntity merchant
                ? MountMerchantMaintenance.repair((ServerWorld)guard.getWorld(), merchant, target)
                : DrakeStableMaintenance.repair((ServerWorld)guard.getWorld(), stable, target, guard))) target = null;
    }
    @Override public void stop() { active = false; target = null; approach = null; guard.getNavigation().stop(); }
}
