package sscextras.client;

import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import sscextras.guardian.GuardianShellEntity;

public final class GuardianShellRenderer extends EntityRenderer<GuardianShellEntity> {
    private final net.minecraft.client.render.entity.model.PlayerEntityModel<GuardianShellEntity> pose;
    public GuardianShellRenderer(EntityRendererFactory.Context context) {
        super(context); shadowRadius = .7f;
        pose = new net.minecraft.client.render.entity.model.PlayerEntityModel<>(context.getPart(net.minecraft.client.render.entity.model.EntityModelLayers.PLAYER), false);
    }
    @Override public Identifier getTexture(GuardianShellEntity entity) { return GuardianMesh.permanentTexture(entity.weathered(), entity.awake(), false); }
    @Override public void render(GuardianShellEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        matrices.push();
        float bodyYaw = net.minecraft.util.math.MathHelper.lerpAngleDegrees(delta, entity.prevBodyYaw, entity.bodyYaw);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-bodyYaw));
        matrices.scale(-.9375f, -.9375f, .9375f); matrices.translate(0, -1.501, 0);
        float stride = entity.awake() ? entity.limbAnimator.getSpeed(delta) : 0;
        float phase = entity.limbAnimator.getPos(delta);
        pose.head.pitch = pose.head.yaw = pose.head.roll = 0;
        pose.leftArm.pitch = pose.rightLeg.pitch = (float)Math.cos(phase * .6662f) * stride * .65f;
        pose.rightArm.pitch = pose.leftLeg.pitch = (float)Math.cos(phase * .6662f + Math.PI) * stride * .65f;
        pose.leftArm.roll = pose.rightArm.roll = pose.leftArm.yaw = pose.rightArm.yaw = 0;
        if (entity.grabbing()) { pose.leftArm.pitch = pose.rightArm.pitch = -1.05f; pose.leftArm.roll = -.18f; pose.rightArm.roll = .18f; }
        PetrificationModelPose.playerModel(pose);
        GuardianMesh.get(entity.stage()).render(matrices, buffers, light, pose, entity.awake(), entity.weathered(), 1, null,
                entity.equipment(), new GuardianMesh.Motion(entity.corePresent(), false, entity.age + delta, phase, stride, !entity.awake()));
        matrices.pop();
        super.render(entity, yaw, delta, matrices, buffers, light);
    }
}
