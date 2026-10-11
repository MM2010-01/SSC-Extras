package sscextras.guardian;

import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;

/** The ritual guide uses vanilla land navigation and collision-aware player movement. */
public final class GuardianEscort extends PathAwareEntity {
    private final ServerPlayerEntity player;
    private final Vec3d destination;
    private Vec3d input=Vec3d.ZERO;
    public GuardianEscort(ServerPlayerEntity player,Vec3d destination) {
        super(EntityType.IRON_GOLEM,player.getServerWorld());this.player=player;this.destination=destination;
        setPosition(player.getPos());calculateDimensions();setStepHeight(player.getStepHeight());
    }
    @Override public EntityDimensions getDimensions(EntityPose pose) {return player==null?super.getDimensions(pose):player.getDimensions(pose);}
    public void guide() {
        age++;setPosition(player.getPos());setOnGround(player.isOnGround());setVelocity(player.getVelocity());
        horizontalCollision=player.horizontalCollision;verticalCollision=player.verticalCollision;
        getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
        if(age%10==1||getNavigation().isIdle()) getNavigation().startMovingTo(destination.x,destination.y,destination.z,.9);
        getNavigation().tick();getMoveControl().tick();getJumpControl().tick();
        player.setJumping(jumping);
        double angle=Math.toRadians(getYaw()-player.getYaw());
        input=forwardSpeed==0?Vec3d.ZERO:new Vec3d(-Math.sin(angle),0,Math.cos(angle));
    }
    public Vec3d input(){return input;}
    public void stop(){getNavigation().stop();player.setJumping(false);discard();}
}
