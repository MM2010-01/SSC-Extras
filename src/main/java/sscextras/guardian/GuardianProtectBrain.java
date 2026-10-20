package sscextras.guardian;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/** An unspawned vanilla navigator drives the player's existing body. */
public final class GuardianProtectBrain extends PathAwareEntity {
    private final ServerPlayerEntity player;
    private final LivingEntity offender;
    private boolean flying;
    private Vec3d input = Vec3d.ZERO;
    public GuardianProtectBrain(ServerPlayerEntity player, LivingEntity offender) {
        super(EntityType.IRON_GOLEM, player.getServerWorld()); this.player = player; this.offender = offender;
        setPosition(player.getPos()); calculateDimensions(); setStepHeight(player.getStepHeight());
        ((MobNavigation)getNavigation()).setCanSwim(true);
    }
    @Override public EntityDimensions getDimensions(EntityPose pose) { return player == null ? super.getDimensions(pose) : player.getDimensions(pose); }
    public boolean valid() {
        return offender.isAlive() && offender.getWorld() == player.getWorld() && offender.squaredDistanceTo(player) <= 256
                && GuardianVillages.offender(player) == offender && !GuardianCombat.locked(player);
    }
    public boolean route() {
        setPosition(player.getPos()); setOnGround(player.isOnGround());
        var path = getNavigation().findPathTo(offender, 0);
        if (path != null && path.reachesTarget()) { flying = false; getNavigation().startMovingAlong(path, 1.2); return true; }
        Vec3d delta = offender.getPos().subtract(player.getPos());
        if (RuneboundGuardian.state(player).energy <= 0 || !player.getWorld().isSpaceEmpty(player, player.getBoundingBox().stretch(delta))) return false;
        flying = true; getNavigation().stop(); return true;
    }
    public void think() {
        age++;
        setPosition(player.getPos()); setOnGround(player.isOnGround()); setVelocity(player.getVelocity());
        horizontalCollision = player.horizontalCollision; verticalCollision = player.verticalCollision;
        if (age % 10 == 0 && !route()) { GuardianVillages.stopProtecting(player); return; }
        player.setSprinting(true);
        Vec3d delta = offender.getPos().subtract(player.getPos());
        if (flying) {
            player.getAbilities().flying = true;
            if (!player.getWorld().isSpaceEmpty(player, player.getBoundingBox().stretch(delta))) { GuardianVillages.stopProtecting(player); return; }
            double speed = io.github.apace100.apoli.component.PowerHolderComponent.modify(player,
                    io.github.apace100.apoli.power.ModifyAirSpeedPower.class,
                    player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * 2);
            player.setVelocity(delta.normalize().multiply(Math.min(speed, delta.length())));
            player.fallDistance = 0; input = Vec3d.ZERO;
        } else {
            if (player.getAbilities().flying) { player.getAbilities().flying = false; player.sendAbilitiesUpdate(); }
            getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
            getNavigation().tick(); getMoveControl().tick(); getJumpControl().tick();
            player.setYaw(getYaw()); player.setHeadYaw(getYaw()); player.bodyYaw = getYaw(); player.setJumping(jumping);
            input = new Vec3d(0, 0, forwardSpeed == 0 ? 0 : 1);
        }
        double reach = Math.pow(player.getWidth() + offender.getWidth() + 1, 2);
        if (delta.lengthSquared() <= reach && player.canSee(offender) && player.getAttackCooldownProgress(.5f) >= 1) {
            player.swingHand(Hand.MAIN_HAND); player.attack(offender);
        }
    }
    public Vec3d movement() { return input; }
    public void stop() {
        getNavigation().stop(); player.setJumping(false);
        if (flying && !player.isCreative() && !player.isSpectator()) { player.getAbilities().flying = false; player.sendAbilitiesUpdate(); }
        discard();
    }
}
