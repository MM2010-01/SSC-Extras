package sscextras.drake;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import java.util.EnumSet;

/** Unspawned navigator: native mob goals drive the existing player body. */
final class FeralDrakeBrain extends PathAwareEntity implements FeralBrain {
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
        ((FeralMobBrain.Bridge)(Object)this).sscExtras$bindFeralPlayer(player, false);
        goalSelector.add(2, new FeralForageGoal(this, player));
        FeralForageGoal.encourageRoaming(this);
        var claim = DrakeOutpostOwnership.claim(player);
        setPositionTarget(claim != null && claim.world.equals(player.getWorld().getRegistryKey()) ? claim.bed() : player.getBlockPos(), 24);
        ((net.minecraft.entity.ai.pathing.MobNavigation)getNavigation()).setCanSwim(true);
    }

    @Override public EntityDimensions getDimensions(EntityPose pose) { return player == null ? super.getDimensions(pose) : player.getDimensions(pose); }
    @Override public net.minecraft.world.World world() { return getWorld(); }
    @Override public boolean isTouchingWater() { return player != null && player.isTouchingWater(); }
    @Override public boolean isInLava() { return player != null && player.isInLava(); }
    @Override public double getFluidHeight(net.minecraft.registry.tag.TagKey<net.minecraft.fluid.Fluid> fluid) {
        return player == null ? super.getFluidHeight(fluid) : player.getFluidHeight(fluid);
    }

    @Override protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(1, new ReturnGoal());
        goalSelector.add(3, new SleepGoal());
        goalSelector.add(4, new HuntGoal());
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

    public void think() {
        age++;
        setPosition(player.getPos()); setOnGround(player.isOnGround());
        setVelocity(player.getVelocity()); horizontalCollision = player.horizontalCollision;
        getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
        FeralForageGoal.eatHeldFood(this, player);
        getVisibilityCache().clear();
        goalSelector.tick();
        getNavigation().tick(); getMoveControl().tick(); getLookControl().tick(); getJumpControl().tick();
        player.setYaw(getYaw()); player.setHeadYaw(getHeadYaw()); player.bodyYaw = getYaw();
        player.setPitch(getPitch()); player.setJumping(jumping);
        player.setSneaking(false); player.setSprinting(false);
    }

    public Vec3d movement() { return new Vec3d(0, 0, forwardSpeed == 0 ? 0 : Math.min(1, getMoveControl().getSpeed())); }

    @Override public void stop() {
        goalSelector.getRunningGoals().forEach(PrioritizedGoal::stop);
        getNavigation().stop();
        ((FeralMobBrain.Bridge)(Object)this).sscExtras$bindFeralPlayer(null, false);
        discard();
    }

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

    private final class HuntGoal extends MeleeAttackGoal {
        private int nextSearch;
        HuntGoal() { super(FeralDrakeBrain.this, 1, true); }
        private boolean hungry() { return player != null && player.canConsume(false) && !player.isUsingItem(); }
        private boolean prey(LivingEntity entity) {
            var type = entity.getType();
            var holder = DrakeLeashing.holder(player);
            return !entity.hasCustomName() && isInWalkTargetRange(entity.getBlockPos())
                    && (holder == null || holder.squaredDistanceTo(entity) <= 25)
                    && (type == EntityType.COW || type == EntityType.PIG || type == EntityType.SHEEP
                    || type == EntityType.CHICKEN || type == EntityType.RABBIT || type == EntityType.COD
                    || type == EntityType.SALMON);
        }
        @Override public boolean canStart() {
            if (age < nextSearch || !hungry()) return false;
            nextSearch = age + 20;
            var predicate = TargetPredicate.createAttackable().setBaseMaxDistance(12).setPredicate(this::prey);
            var target = getWorld().getClosestEntity(getWorld().getEntitiesByClass(LivingEntity.class,
                    player.getBoundingBox().expand(12, 4, 12), entity -> entity.isAlive()), predicate,
                    FeralDrakeBrain.this, getX(), getEyeY(), getZ());
            setTarget(target);
            if (super.canStart()) return true;
            setTarget(null);
            return false;
        }
        @Override public boolean shouldContinue() { return hungry() && getTarget() != null && prey(getTarget()) && super.shouldContinue(); }
        @Override protected void attack(LivingEntity target, double distance) {
            if (distance <= getSquaredMaxAttackDistance(target) && isCooledDown()) player.swingHand(Hand.MAIN_HAND);
            super.attack(target, distance);
        }
        @Override public void stop() { super.stop(); setTarget(null); }
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
