package sscextras.drake;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.Comparator;
import java.util.EnumSet;

/** A native mob's AI controls the player without spawning a second body. */
public final class FeralMobBrain implements FeralBrain {
    public interface Bridge {
        ServerPlayerEntity sscExtras$feralPlayer();
        void sscExtras$bindFeralPlayer(ServerPlayerEntity player, boolean familiar);
        void sscExtras$thinkForPlayer();
        GoalSelector sscExtras$feralGoals();
    }

    private final ServerPlayerEntity player;
    final MobEntity mob;
    private final boolean originalNoGravity;

    FeralMobBrain(ServerPlayerEntity player, FeralForm form) {
        this.player = player;
        originalNoGravity = player.hasNoGravity();
        var entity = form.type().create(player.getServerWorld());
        mob = entity instanceof MobEntity nativeMob ? nativeMob : EntityType.ZOMBIE.create(player.getServerWorld());
        mob.setPosition(player.getPos());
        if (mob instanceof FoxEntity fox) {
            fox.initialize(player.getServerWorld(), player.getServerWorld().getLocalDifficulty(player.getBlockPos()),
                    SpawnReason.COMMAND, new FoxEntity.FoxData(form.snowFox() ? FoxEntity.Type.SNOW : FoxEntity.Type.RED), null);
            fox.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, net.minecraft.item.ItemStack.EMPTY);
        }
        ((Bridge)mob).sscExtras$bindFeralPlayer(player, form.familiar());
        FeralForageGoal.encourageRoaming(mob);
        if (mob instanceof PathAwareEntity && !mob.getBrain().isMemoryInState(MemoryModuleType.WALK_TARGET, MemoryModuleState.REGISTERED)) {
            ((Bridge)mob).sscExtras$feralGoals().add(4, new FeralForageGoal(mob, player));
        }
        if (form.familiar() && mob instanceof PathAwareEntity fox) {
            ((Bridge)mob).sscExtras$feralGoals().add(3, new FollowWitchGoal(fox));
        }
        if (mob instanceof BatEntity bat) bat.setRoosting(false);
    }

    @Override public World world() { return mob.getWorld(); }

    @Override public void think() {
        // Vanilla despawn checks normally reset this near players; the unspawned proxy skips them.
        mob.setDespawnCounter(0);
        ((Bridge)mob).sscExtras$thinkForPlayer();
        FeralForageGoal.eatHeldFood(mob, player);
        player.setNoGravity(originalNoGravity || mob.hasNoGravity() || mob instanceof BatEntity);
        player.setVelocity(mob.getVelocity());
    }

    @Override public Vec3d movement() {
        double speed = mob.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed <= 0) return Vec3d.ZERO;
        return new Vec3d(MathHelper.clamp(mob.sidewaysSpeed / speed, -1, 1),
                MathHelper.clamp(mob.upwardSpeed / speed, -1, 1),
                mob instanceof BatEntity ? 0 : MathHelper.clamp(mob.forwardSpeed / speed, -1, 1));
    }

    @Override public void stop() {
        player.setNoGravity(originalNoGravity);
        mob.getNavigation().stop();
        ((Bridge)mob).sscExtras$bindFeralPlayer(null, false);
        mob.discard();
    }

    private final class FollowWitchGoal extends Goal {
        private final PathAwareEntity fox;
        private WitchEntity witch;
        private int nextSearch, nextPath;

        FollowWitchGoal(PathAwareEntity fox) {
            this.fox = fox;
            setControls(EnumSet.of(Control.MOVE, Control.LOOK));
        }

        @Override public boolean canStart() {
            if (mob.age < nextSearch) return false;
            nextSearch = mob.age + 20;
            witch = world().getEntitiesByClass(WitchEntity.class, player.getBoundingBox().expand(24),
                    candidate -> candidate.isAlive()).stream()
                    .min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
            return witch != null && player.squaredDistanceTo(witch) > 9;
        }

        @Override public boolean shouldContinue() {
            return witch != null && witch.isAlive() && player.squaredDistanceTo(witch) > 4
                    && player.squaredDistanceTo(witch) < 1024;
        }

        @Override public boolean shouldRunEveryTick() { return true; }

        @Override public void tick() {
            fox.getLookControl().lookAt(witch);
            if (mob.age >= nextPath) {
                nextPath = mob.age + 10;
                fox.getNavigation().startMovingTo(witch, player.squaredDistanceTo(witch) > 64 ? 1.3 : 1);
            }
        }

        @Override public void stop() { witch = null; fox.getNavigation().stop(); }
    }
}
