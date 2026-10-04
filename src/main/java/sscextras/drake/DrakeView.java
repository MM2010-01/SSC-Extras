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
        if (!(entity instanceof PlayerEntity player) || EarthenDrake.stage(player) != 3
                || player.isSleeping()) return eyes;
        double width = ScaleUtils.getModelWidthScale(player, tickDelta) / 1.5;
        double height = ScaleUtils.getModelHeightScale(player, tickDelta) / 1.5;
        double bodyYaw = Math.toRadians(MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw));
        double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw));
        double pitch = Math.toRadians(player.getPitch(tickDelta));
        // The skull pivots 0.68 blocks ahead of the body; the eyes sit above and ahead of it.
        double forward = .206 * Math.cos(pitch) + .104 * Math.sin(pitch);
        Vec3d offset = new Vec3d((-Math.sin(bodyYaw)*.68-Math.sin(yaw)*forward)*width,
                (.104*(Math.cos(pitch)-1)-.206*Math.sin(pitch))*height,
                (Math.cos(bodyYaw)*.68+Math.cos(yaw)*forward)*width);
        Vec3d head = eyes.add(offset);
        var hit = player.getWorld().raycast(new RaycastContext(eyes, head,
                RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? head
                : eyes.add(offset.multiply(Math.max(0, eyes.distanceTo(hit.getPos())-.05) / offset.length()));
    }
}
