package sscextras.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import sscextras.collar.CollarSlots;
import sscextras.collar.Collars;
import sscextras.drake.EarthenDrake;

public final class CollarFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final CollarModel model = new CollarModel();
    private final CuffModel taming = new CuffModel(7.5f, 7.5f, true);

    public CollarFeatureRenderer(PlayerEntityRenderer renderer) {
        super(renderer);
    }

    @Override public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light,
            AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
            float animationProgress, float headYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator()) return;
        var collar = CollarSlots.visibleCollar(player);
        if (collar.isEmpty()) return;
        matrices.push();
        int drakeStage = EarthenDrake.stage(player);
        if (drakeStage == 3) {
            var body = getContextModel().body;
            matrices.translate(body.pivotX / 16, (body.pivotY - 15.893333f) / 16, (body.pivotZ + 6.4f) / 16);
            matrices.multiply(new org.joml.Quaternionf().rotationZYX(body.roll, body.yaw, body.pitch - (float)Math.PI / 2));
            matrices.translate(0, 0.6475, -0.410625);
        } else getContextModel().head.rotate(matrices);
        matrices.translate(0, drakeStage == 2 ? 0.18 : drakeStage == 3 ? 0.14 : 1.0 / 16,
                drakeStage == 2 ? 0.09 : drakeStage == 3 ? 0.07 : 0);
        if (drakeStage == 3) matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(35));
        float scale = drakeStage == 2 ? 0.72f : drakeStage == 3 ? 0.65f : 0.8f;
        matrices.scale(drakeStage == 3 ? 0.66f : scale, scale, drakeStage == 3 ? 0.64f : scale);
        if (collar.isOf(Collars.TAMING)) {
            taming.render(matrices, vertices, light, false);
            matrices.pop();
            return;
        }
        model.render(matrices, vertices, light, collar.isOf(Collars.CURSED));
        matrices.pop();
    }
}
