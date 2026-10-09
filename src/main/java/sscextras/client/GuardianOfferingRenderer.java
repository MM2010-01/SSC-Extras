package sscextras.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.*;
import sscextras.guardian.GuardianOffering;

public final class GuardianOfferingRenderer implements BlockEntityRenderer<GuardianOffering> {
    public GuardianOfferingRenderer(BlockEntityRendererFactory.Context context) { }
    @Override public void render(GuardianOffering offering, float delta, MatrixStack matrices, VertexConsumerProvider buffers, int light, int overlay) {
        if (!offering.available() || offering.getWorld() == null) return;
        var player = offering.target == null ? null : offering.getWorld().getPlayerByUuid(offering.target);
        float progress = player == null ? 0 : MathHelper.clamp((offering.getWorld().getTime() - offering.started + delta) / GuardianOffering.DURATION, 0, 1);
        float t = progress * progress * (3 - 2 * progress);
        for (int piece = 0; piece < 5; piece++) {
            boolean collar = piece == 4, hand = piece < 2;
            float side = (piece & 1) == 0 ? -1 : 1;
            Vec3d from = collar ? new Vec3d(1, .755, .6) : new Vec3d(1 + side * .43, .755, hand ? 1.02 : 1.55);
            Vec3d at = from;
            if (player != null) {
                Vec3d offset = new Vec3d(collar ? 0 : side * (hand ? .37 : .12), collar ? 1.48 : hand ? .86 : .19, 0)
                        .rotateY(-player.bodyYaw * MathHelper.RADIANS_PER_DEGREE);
                Vec3d to = player.getLerpedPos(delta).add(offset).subtract(Vec3d.of(offering.getPos()));
                at = from.lerp(to, t).add(0, Math.sin(progress * Math.PI) * .55, 0);
            }
            matrices.push(); matrices.translate(at.x, at.y, at.z);
            if (player != null) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-player.bodyYaw * t));
            GuardianAdornmentMesh.render("display/" + (collar ? "collar" : hand ? "wrist" : "ankle"), matrices, buffers, light);
            matrices.pop();
        }
    }
    @Override public boolean rendersOutsideBoundingBox(GuardianOffering offering) { return true; }
}
