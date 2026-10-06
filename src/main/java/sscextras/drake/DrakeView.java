package sscextras.drake;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import virtuoel.pehkui.util.ScaleUtils;

public final class DrakeView {
    private DrakeView() { }

    public static Vec3d atHead(Entity entity, float tickDelta, Vec3d eyes) {
        if (!(entity instanceof PlayerEntity player) || player.isSleeping()) return eyes;
        int stage = EarthenDrake.stage(player);
        if (stage < 0 || stage < 2 && !EarthenDrake.onAllFours(player)) return eyes;
        double scale = stage == 3 ? 1.5 : 1;
        double width = ScaleUtils.getModelWidthScale(player, tickDelta) / scale;
        double height = ScaleUtils.getModelHeightScale(player, tickDelta) / scale;
        double bodyYaw = Math.toRadians(MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw));
        double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw));
        double pitch = Math.toRadians(player.getPitch(tickDelta));
        double pivot = stage == 3 ? .68 : EarthenDrake.onAllFours(player) ? .375 : .1;
        double forward = stage == 3 ? .206 * Math.cos(pitch) + .104 * Math.sin(pitch) : .3;
        double vertical = stage == 3 ? .104 * (Math.cos(pitch) - 1) - .206 * Math.sin(pitch) : 0;
        Vec3d offset = new Vec3d((-Math.sin(bodyYaw)*pivot-Math.sin(yaw)*forward)*width,
                vertical*height, (Math.cos(bodyYaw)*pivot+Math.cos(yaw)*forward)*width);
        Vec3d head = eyes.add(offset);
        var hit = player.getWorld().raycast(new RaycastContext(eyes, head,
                RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? head
                : eyes.add(offset.multiply(Math.max(0, eyes.distanceTo(hit.getPos())-.05) / offset.length()));
    }
}
