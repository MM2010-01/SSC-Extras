package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import sscextras.drake.DrakeFaction;
import sscextras.drake.FeralMobBrain;

@Mixin(MobEntity.class)
public abstract class FeralMobBrainMixin extends LivingEntity implements FeralMobBrain.Bridge {
    @Unique private ServerPlayerEntity sscExtras$feralPlayer;
    @Unique private boolean sscExtras$familiar;
    @Unique private int sscExtras$lastAttacked;
    @Shadow protected abstract void tickNewAi();
    @Shadow protected GoalSelector goalSelector;

    protected FeralMobBrainMixin(EntityType<? extends LivingEntity> type, World world) { super(type, world); }

    public ServerPlayerEntity sscExtras$feralPlayer() { return sscExtras$feralPlayer; }
    public GoalSelector sscExtras$feralGoals() { return goalSelector; }

    public void sscExtras$bindFeralPlayer(ServerPlayerEntity player, boolean familiar) {
        sscExtras$feralPlayer = player;
        sscExtras$familiar = familiar;
        if (player != null) {
            calculateDimensions();
            setStepHeight(player.getStepHeight());
        }
    }

    public void sscExtras$thinkForPlayer() {
        var player = sscExtras$feralPlayer;
        age++;
        if (getWidth() != player.getWidth() || getHeight() != player.getHeight()) calculateDimensions();
        setPosition(player.getPos()); setBoundingBox(player.getBoundingBox()); setOnGround(player.isOnGround());
        setVelocity(player.getVelocity()); horizontalCollision = player.horizontalCollision;
        verticalCollision = player.verticalCollision;
        touchingWater = player.isTouchingWater(); submergedInWater = player.isSubmergedInWater();
        setYaw(player.getYaw()); setPitch(player.getPitch()); headYaw = player.headYaw; bodyYaw = player.bodyYaw;
        getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(player.getMaxHealth());
        setHealth(player.getHealth());
        var attack = getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE));
        if (sscExtras$lastAttacked != player.getLastAttackedTime()) {
            sscExtras$lastAttacked = player.getLastAttackedTime();
            setAttacker(player.getAttacker());
        }
        tickNewAi();
        player.setYaw(getYaw()); player.setHeadYaw(getHeadYaw()); player.bodyYaw = bodyYaw;
        player.setPitch(getPitch()); player.setJumping(jumping);
        player.setSneaking(false); player.setSprinting(false);
    }

    @Override public EntityDimensions getDimensions(EntityPose pose) {
        return sscExtras$feralPlayer == null ? super.getDimensions(pose) : sscExtras$feralPlayer.getDimensions(pose);
    }
    @Override public boolean isTouchingWater() { return sscExtras$feralPlayer == null ? super.isTouchingWater() : sscExtras$feralPlayer.isTouchingWater(); }
    @Override public boolean isSubmergedInWater() { return sscExtras$feralPlayer == null ? super.isSubmergedInWater() : sscExtras$feralPlayer.isSubmergedInWater(); }
    @Override public boolean isSubmergedIn(TagKey<Fluid> fluid) { return sscExtras$feralPlayer == null ? super.isSubmergedIn(fluid) : sscExtras$feralPlayer.isSubmergedIn(fluid); }
    @Override public boolean isInLava() { return sscExtras$feralPlayer == null ? super.isInLava() : sscExtras$feralPlayer.isInLava(); }
    @Override public double getFluidHeight(TagKey<Fluid> fluid) { return sscExtras$feralPlayer == null ? super.getFluidHeight(fluid) : sscExtras$feralPlayer.getFluidHeight(fluid); }

    @Override public boolean canTarget(LivingEntity target) {
        return (sscExtras$feralPlayer == null || target != sscExtras$feralPlayer
                && !DrakeFaction.blocksAttack(sscExtras$feralPlayer, target) && !(sscExtras$familiar && target instanceof WitchEntity))
                && super.canTarget(target);
    }

    @Override public boolean isTeammate(Entity other) {
        return sscExtras$feralPlayer == null ? super.isTeammate(other) : other == sscExtras$feralPlayer
                || sscExtras$feralPlayer.isTeammate(other) || sscExtras$familiar && other instanceof WitchEntity;
    }
}
