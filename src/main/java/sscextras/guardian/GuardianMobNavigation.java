package sscextras.guardian;

import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** Resident-only adapter: the existing village goal selects one native ground/air controller at a time. */
final class GuardianMobNavigation {
    private final GuardianShellEntity body;
    final EntityNavigation ground, air;
    final MoveControl walking, flight;
    GuardianMobNavigation(GuardianShellEntity body) {
        this.body = body; ground = body.getNavigation(); walking = body.getMoveControl();
        air = new BirdNavigation(body, body.getWorld()); air.setCanSwim(true);
        ((BirdNavigation)air).setCanEnterOpenDoors(true);
        flight = new FlightMoveControl(body, 20, true);
    }
    boolean reachable(Vec3d target) {
        if (!body.powered() || body.core.isEmpty()) return false;
        var path = ground.findPathTo(BlockPos.ofFloored(target), 1);
        if (path != null && path.reachesTarget()) return true;
        path = body.energy > 0 ? air.findPathTo(airTarget(target), 1) : null;
        return path != null && path.reachesTarget();
    }
    /** Flight is a fallback; requireFlight is only for physically blocked carry clearance. */
    boolean moveTo(Vec3d target, double speed, boolean requireFlight) {
        if (!body.powered() || body.core.isEmpty()) return false;
        // An airborne walking mob cannot search for a land path, even when its last node just completed.
        // Wait for the step/jump to land instead of treating that temporary limitation as an air-only route.
        if (!requireFlight && !body.flying() && !body.isOnGround() && !body.isTouchingWater()
                && !body.isInLava() && !body.hasVehicle()) {
            ground.setSpeed(speed); return !ground.isIdle();
        }
        var path = !requireFlight ? ground.findPathTo(BlockPos.ofFloored(target), 1) : null;
        boolean fly = path == null || !path.reachesTarget();
        if (fly && body.squaredDistanceTo(target) < 9
                && body.getWorld().isSpaceEmpty(body, body.getBoundingBox().stretch(target.subtract(body.getPos())))) {
            body.flightMode(true); body.getNavigation().stop();
            body.getMoveControl().moveTo(target.x, target.y, target.z, speed); return true;
        }
        if (fly) path = air.findPathTo(airTarget(target), 0);
        if (path == null || !path.reachesTarget()) return false;
        body.flightMode(fly); body.getNavigation().startMovingAlong(path, speed);
        return true;
    }
    private static BlockPos airTarget(Vec3d target) {
        // A pedestal's .75-height surface is inside a solid block node; flying nodes need clear whole-block feet.
        return BlockPos.ofFloored(target.x, Math.ceil(target.y), target.z);
    }
    void stop(boolean land) {
        body.getNavigation().stop();
        body.getMoveControl().moveTo(body.getX(), body.getY(), body.getZ(), 0);
        body.setVelocity(Vec3d.ZERO); body.setForwardSpeed(0); body.setUpwardSpeed(0);
        if (land) body.flightMode(false);
    }
}
