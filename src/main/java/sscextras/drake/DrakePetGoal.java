package sscextras.drake;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import java.util.ArrayList;
import java.util.EnumSet;

public final class DrakePetGoal extends Goal {
    private final MobEntity actor;
    private DrakeStablePiece stable;
    private LivingEntity drake;
    private int nextSearch, started, petted, duration;

    public DrakePetGoal(MobEntity actor) {
        this.actor = actor;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    private boolean idle() {
        return actor.isAlive() && !actor.isAiDisabled() && !actor.hasVehicle() && !actor.hasPassengers()
                && actor.hurtTime == 0 && actor.getTarget() == null
                && (!(actor instanceof PillagerEntity pillager) || !pillager.hasActiveRaid())
                && (!(actor instanceof DrakeVisitorEntity visitor) || !visitor.leaving());
    }

    private boolean eligible(LivingEntity candidate) {
        if (!candidate.isAlive() || candidate.hurtTime > 0 || candidate.hasPassengers() || candidate.hasVehicle()
                || DrakeBattleGoal.assigned(candidate) || !DrakeAttention.available(candidate, actor)
                || !DrakeCaptureGoal.near(stable.getBoundingBox(), candidate.getPos(), DrakeRoaming.RANGE)) return false;
        if (candidate instanceof PlayerEntity player) {
            var claim = DrakeOutpostOwnership.claim(player);
            if (player.isCreative() || player.isSpectator() || player.isSleeping() || EarthenDrake.stage(player) < 0
                    || claim == null || !claim.matches(actor.getWorld(), stable) || claim.tryingToEscape
                    || DrakeLeashing.holder(player) instanceof LivingEntity
                    || !actor.getWorld().isDay() && !claim.stall().contains(player.getPos())
                    || BondOfTheBeastCompat.hasOwner(player)) return false;
        } else if (!(candidate instanceof StableDrakeEntity resident) || !resident.belongsTo(stable) || resident.getTarget() != null) return false;
        return actor instanceof DrakeVisitorEntity || !stable.getBoundingBox().contains(candidate.getBlockPos());
    }

    @Override public boolean canStart() {
        if (actor.age < nextSearch || !idle()) return false;
        nextSearch = actor.age + 200;
        stable = actor instanceof DrakeVisitorEntity visitor ? visitor.stable() : ((DrakeStableNavigation)actor.getNavigation()).stable();
        if (stable == null || !(actor instanceof DrakeVisitorEntity) && actor.getRandom().nextInt(6) != 0) return false;
        var candidates = new ArrayList<LivingEntity>();
        candidates.addAll(actor.getWorld().getEntitiesByClass(StableDrakeEntity.class, actor.getBoundingBox().expand(24), this::eligible));
        for (var player : actor.getWorld().getPlayers()) if (actor.squaredDistanceTo(player) <= 24 * 24 && eligible(player)) candidates.add(player);
        while (!candidates.isEmpty()) {
            var candidate = candidates.remove(actor.getRandom().nextInt(candidates.size()));
            if (!actor.getVisibilityCache().canSee(candidate)) continue;
            var path = actor.getNavigation().findPathTo(candidate, 1);
            if (path != null && path.reachesTarget()) { drake = candidate; return true; }
        }
        return false;
    }

    @Override public void start() {
        started = actor.age; petted = 0; duration = 100 + actor.getRandom().nextInt(61);
        ((DrakeAttention.State)drake).sscExtras$petter(actor);
        actor.clearActiveItem();
    }

    @Override public boolean shouldContinue() {
        return drake != null && idle() && actor.age - started < 400 && petted < duration
                && drake.getWorld() == actor.getWorld() && eligible(drake) && actor.squaredDistanceTo(drake) <= 28 * 28;
    }

    @Override public boolean shouldRunEveryTick() { return true; }

    @Override public void tick() {
        actor.getLookControl().lookAt(drake, 30, 30);
        double reach = (actor.getWidth() + drake.getWidth()) * .5 + .8;
        if (actor.squaredDistanceTo(drake) > reach * reach || !actor.getVisibilityCache().canSee(drake)) {
            ((DrakeAttention.State)drake).sscExtras$petUntil(0);
            ((DrakeAttention.State)actor).sscExtras$petting(false);
            if ((actor.age - started) % 10 == 0) actor.getNavigation().startMovingTo(drake, .65);
            return;
        }
        actor.getNavigation().stop();
        ((DrakeAttention.State)actor).sscExtras$petting(true);
        if (drake instanceof StableDrakeEntity resident) resident.getNavigation().stop();
        if (petted % 20 == 0) {
            ((DrakeAttention.State)drake).sscExtras$petUntil(actor.getWorld().getTime() + 22);
            actor.swingHand(Hand.OFF_HAND);
            ((ServerWorld)actor.getWorld()).spawnParticles(ParticleTypes.HEART, drake.getX(), drake.getEyeY() + .25,
                    drake.getZ(), 1, .2, .12, .2, .01);
            if (drake instanceof PlayerEntity player) DrakeInstinct.pet(player);
        }
        petted++;
    }

    @Override public void stop() {
        ((DrakeAttention.State)actor).sscExtras$petting(false);
        if (drake != null) DrakeAttention.finish(drake, actor, petted > 0);
        if (petted > 0 && actor instanceof DrakeVisitorEntity visitor) visitor.finishedPet();
        drake = null;
        actor.getNavigation().stop();
        nextSearch = actor.age + (actor instanceof DrakeVisitorEntity ? 40 : 600);
    }
}
