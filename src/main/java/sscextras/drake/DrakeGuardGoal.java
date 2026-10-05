package sscextras.drake;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import java.util.EnumSet;

public final class DrakeGuardGoal extends Goal {
    private final PillagerEntity pillager;
    private final boolean followOnly;
    private PlayerEntity following;
    private Path patrol;
    private int nextPatrol, nextPath;
    private boolean correcting;

    public DrakeGuardGoal(PillagerEntity pillager) {
        this(pillager, false);
    }

    public DrakeGuardGoal(PillagerEntity pillager, boolean followOnly) {
        this.pillager = pillager;
        this.followOnly = followOnly;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    private DrakeStableNavigation navigation() { return (DrakeStableNavigation)pillager.getNavigation(); }

    @Override public boolean canStart() {
        if (pillager.hasVehicle() || pillager.hasActiveRaid() || pillager.getTarget() != null) return false;
        var stable = navigation().stable();
        if (stable == null) return false;
        following = DrakeRoaming.following(pillager);
        if (followOnly) return following != null;
        if (following != null) return false;
        if (pillager.age < nextPatrol) return false;
        nextPatrol = pillager.age + 40 + pillager.getRandom().nextInt(60);
        patrol = patrolPath(pillager, stable);
        return patrol != null;
    }

    public static Path patrolPath(PillagerEntity pillager, DrakeStablePiece stable) {
        var box = stable.getBoundingBox();
        int radius = DrakeStableLayout.roamRange(box) - 4;
        for (int i = 0; i < 8; i++) {
            int x = box.getMinX() - radius + pillager.getRandom().nextInt(box.getBlockCountX() + radius * 2);
            int z = box.getMinZ() - radius + pillager.getRandom().nextInt(box.getBlockCountZ() + radius * 2);
            var column = new BlockPos(x, 0, z);
            if (!pillager.getWorld().isChunkLoaded(column) || !DrakeCaptureGoal.near(box, Vec3d.ofCenter(column), DrakeStableLayout.roamRange(stable.getBoundingBox()))) continue;
            var pos = pillager.getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
            if (box.contains(pos) || pillager.squaredDistanceTo(Vec3d.ofBottomCenter(pos)) < 16) continue;
            var path = ((DrakeStableNavigation)pillager.getNavigation()).findPathTo(pos, 0);
            if (path != null && path.reachesTarget()) return path;
        }
        return null;
    }

    @Override public void start() {
        pillager.clearActiveItem(); pillager.setCharging(false);
        nextPath = 0;
        correcting = following != null && DrakeLeashing.holder(following) == pillager;
        if (following != null) ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(new ItemStack(Items.LEAD));
        if (following == null) navigation().startMovingAlong(patrol, .65);
    }

    @Override public boolean shouldContinue() {
        return !pillager.hasVehicle() && !pillager.hasActiveRaid() && pillager.getTarget() == null
                && (following != null ? following == DrakeRoaming.following(pillager) : !navigation().isIdle());
    }

    @Override public void tick() {
        if (following == null) return;
        var claim = DrakeOutpostOwnership.claim(following);
        if (claim == null) return;
        pillager.setSprinting(claim.tryingToEscape);
        pillager.getLookControl().lookAt(following, 30, 30);
        boolean inside = DrakeCaptureGoal.near(claim.stable, following.getPos(), DrakeStableLayout.roamRange(claim.stable));
        if (!inside && !claim.tryingToEscape && !DrakeLeashing.attached(following)
                && pillager.squaredDistanceTo(following) <= 4 && pillager.getVisibilityCache().canSee(following)
                && DrakeLeashing.attachPillager(following, pillager)) {
            correcting = true; claim.escort = pillager.getId(); nextPath = 0;
            DrakeDialogue.say(following, "range_return");
        }
        if (correcting && DrakeLeashing.holder(following) == pillager) {
            if (inside) {
                DrakeLeashing.detach(following, false); correcting = false; nextPath = 0;
            } else {
                if (pillager.squaredDistanceTo(following) > 49) navigation().stop();
                else if (pillager.age >= nextPath) {
                    nextPath = pillager.age + 10;
                    var nearest = new Vec3d(MathHelper.clamp(following.getX(), claim.stable.getMinX(), claim.stable.getMaxX() + 1),
                            following.getY(), MathHelper.clamp(following.getZ(), claim.stable.getMinZ(), claim.stable.getMaxZ() + 1));
                    var point = nearest.add(following.getPos().subtract(nearest).normalize()
                            .multiply(DrakeStableLayout.roamRange(claim.stable) - 8));
                    var destination = pillager.getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(point));
                    navigation().startMovingTo(destination.getX() + .5, destination.getY(), destination.getZ() + .5, .8);
                }
                return;
            }
        }
        if (pillager.squaredDistanceTo(following) < (inside || claim.tryingToEscape ? 9 : 4)) navigation().stop();
        else if (pillager.age >= nextPath) {
            nextPath = pillager.age + 10;
            navigation().startMovingTo(following, 1);
        }
    }

    @Override public boolean shouldRunEveryTick() { return true; }

    @Override public void stop() {
        var claim = following == null ? null : DrakeOutpostOwnership.claim(following);
        if (correcting && following != null && DrakeLeashing.holder(following) == pillager
                && !(claim != null && claim.tryingToEscape && DrakeCaptureGoal.eligible(following))
                && ((DrakeCaptureGoal.Captor)pillager).sscExtras$captureGoal().quarry() != following)
            DrakeLeashing.detach(following, false);
        if (following != null) ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(ItemStack.EMPTY);
        correcting = false; following = null; patrol = null; pillager.setSprinting(false); navigation().stop();
    }
}
