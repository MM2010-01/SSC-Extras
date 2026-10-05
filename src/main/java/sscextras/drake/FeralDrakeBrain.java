package sscextras.drake;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.Comparator;
import java.util.EnumSet;

/** Unspawned navigator: native mob goals drive the existing player body. */
final class FeralDrakeBrain extends PathAwareEntity {
    private final ServerPlayerEntity player;
    private final boolean returning;

    FeralDrakeBrain(ServerPlayerEntity player) {
        this(player, false);
    }

    FeralDrakeBrain(ServerPlayerEntity player, boolean returning) {
        super(DrakeStable.DRAKE, player.getServerWorld());
        this.player = player;
        this.returning = returning;
        setPosition(player.getPos()); setYaw(player.getYaw());
        calculateDimensions(); setStepHeight(player.getStepHeight());
        var claim = DrakeOutpostOwnership.claim(player);
        setPositionTarget(claim != null && claim.world.equals(player.getWorld().getRegistryKey()) ? claim.bed() : player.getBlockPos(), 24);
        ((net.minecraft.entity.ai.pathing.MobNavigation)getNavigation()).setCanSwim(true);
    }

    @Override public EntityDimensions getDimensions(EntityPose pose) { return player == null ? super.getDimensions(pose) : player.getDimensions(pose); }
    @Override public boolean isTouchingWater() { return player != null && player.isTouchingWater(); }
    @Override public boolean isInLava() { return player != null && player.isInLava(); }
    @Override public double getFluidHeight(net.minecraft.registry.tag.TagKey<net.minecraft.fluid.Fluid> fluid) {
        return player == null ? super.getFluidHeight(fluid) : player.getFluidHeight(fluid);
    }

    @Override protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new ReturnGoal());
        goalSelector.add(2, new ForageGoal());
        goalSelector.add(3, new SleepGoal());
        goalSelector.add(5, new WanderAroundFarGoal(this, .65) {
            @Override protected Vec3d getWanderTarget() {
                var target = super.getWanderTarget();
                var holder = player == null ? null : DrakeLeashing.holder(player);
                return target != null && (holder == null || holder.squaredDistanceTo(target) <= 25) ? target : null;
            }
        });
        goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8) {
            @Override public boolean canStart() { return super.canStart() && target != player; }
        });
        goalSelector.add(7, new LookAroundGoal(this));
    }

    void think() {
        age++;
        setPosition(player.getPos()); setOnGround(player.isOnGround());
        setVelocity(player.getVelocity()); horizontalCollision = player.horizontalCollision;
        getVisibilityCache().clear();
        goalSelector.tick();
        getNavigation().tick(); getMoveControl().tick(); getLookControl().tick(); getJumpControl().tick();
        player.setYaw(getYaw()); player.setHeadYaw(getHeadYaw()); player.bodyYaw = getYaw();
        player.setPitch(getPitch()); player.setJumping(jumping);
        player.setSneaking(false); player.setSprinting(false);
    }

    Vec3d movement() { return new Vec3d(0, 0, forwardSpeed == 0 ? 0 : Math.min(1, getMoveControl().getSpeed())); }

    private final class ReturnGoal extends Goal {
        private int nextPath;
        ReturnGoal() { setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
        @Override public boolean canStart() { return returning; }
        @Override public boolean shouldContinue() { return returning; }
        @Override public boolean shouldRunEveryTick() { return true; }
        @Override public void tick() {
            if (age < nextPath) return;
            nextPath = age + 10;
            var claim = DrakeOutpostOwnership.claim(player);
            if (claim == null) return;
            var box = claim.stable;
            double x = net.minecraft.util.math.MathHelper.clamp(player.getX(), box.getMinX(), box.getMaxX() + 1);
            double z = net.minecraft.util.math.MathHelper.clamp(player.getZ(), box.getMinZ(), box.getMaxZ() + 1);
            var inward = new Vec3d(x - player.getX(), 0, z - player.getZ());
            double distance = inward.length() - DrakeStableLayout.roamRange(box) + 3;
            var target = player.getPos().add(inward.normalize().multiply(Math.min(12, Math.max(1, distance))));
            getLookControl().lookAt(target.x, player.getEyeY(), target.z);
            getNavigation().startMovingTo(target.x, target.y, target.z, .8);
        }
        @Override public void stop() { getNavigation().stop(); }
    }

    private final class ForageGoal extends Goal {
        private ItemEntity food;
        private int nextSearch, nextPath;
        ForageGoal() { setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
        @Override public boolean canStart() {
            if (age < nextSearch || player == null || !player.canConsume(false)) return false;
            nextSearch = age + 20;
            var holder = DrakeLeashing.holder(player);
            food = getWorld().getEntitiesByClass(ItemEntity.class, player.getBoundingBox().expand(8), item -> item.isAlive()
                    && DrakeFeralization.rawFood(item.getStack()) && (holder == null || holder.squaredDistanceTo(item) <= 25)).stream()
                    .min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
            return food != null;
        }
        @Override public boolean shouldContinue() { return food != null && food.isAlive() && player.canConsume(false) && player.squaredDistanceTo(food) < 100; }
        @Override public void tick() {
            getLookControl().lookAt(food);
            if (player.squaredDistanceTo(food) < 2.25) {
                getNavigation().stop();
                if (player.isUsingItem()) return;
                var bite = food.getStack().copyWithCount(1);
                bite.finishUsing(player.getWorld(), player);
                food.getStack().decrement(1);
                if (food.getStack().isEmpty()) food.discard();
                food = null; nextSearch = age + 32;
            } else if (age >= nextPath) {
                nextPath = age + 10; getNavigation().startMovingTo(food, .8);
            }
        }
        @Override public void stop() { food = null; getNavigation().stop(); }
    }

    private final class SleepGoal extends Goal {
        private Vec3d bed;
        private int nextPath;
        SleepGoal() { setControls(EnumSet.of(Control.MOVE)); }
        @Override public boolean canStart() {
            if (player == null || getWorld().isDay() || player.getHungerManager().getFoodLevel() < 10) return false;
            var claim = DrakeOutpostOwnership.claim(player);
            if (claim == null || !claim.world.equals(getWorld().getRegistryKey()) || player.squaredDistanceTo(Vec3d.ofCenter(claim.bed())) > 256) return false;
            bed = Vec3d.ofBottomCenter(claim.bed());
            return DrakeHaySleep.patch(getWorld(), claim.bed().down()) != null;
        }
        @Override public boolean shouldContinue() { return !getWorld().isDay() && !player.isSleeping(); }
        @Override public void tick() {
            if (age < nextPath) return;
            nextPath = age + 20;
            if (player.squaredDistanceTo(bed) < 2.25) {
                getNavigation().stop(); DrakeHaySleep.sleep(player, net.minecraft.util.math.BlockPos.ofFloored(bed).down());
            } else getNavigation().startMovingTo(bed.x, bed.y, bed.z, .65);
        }
        @Override public void stop() { getNavigation().stop(); }
    }
}
