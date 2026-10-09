package sscextras.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import sscextras.guardian.GuardianShellEntity;

public final class GuardianShellRenderer extends EntityRenderer<GuardianShellEntity> {
    public GuardianShellRenderer(EntityRendererFactory.Context context) { super(context); shadowRadius = .7f; }
    @Override public Identifier getTexture(GuardianShellEntity entity) { return GuardianMesh.permanentTexture(entity.weathered(), false, false); }
    @Override public void render(GuardianShellEntity entity, float yaw, float delta, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-yaw));
        matrices.scale(-.9375f, -.9375f, .9375f); matrices.translate(0, -1.501, 0);
        GuardianMesh.get(entity.stage()).render(matrices, buffers, light, null, false, entity.weathered(), 1, null);
        matrices.pop();
        if (entity.work() > 0) {
            matrices.push();
            float height = 2.85f * entity.work() / 100;
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180-yaw));
            matrices.translate(-1.5, 0, -.6); matrices.scale(3, height, 2.65f);
            MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(Blocks.STONE.getDefaultState(), matrices, buffers, light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
        super.render(entity, yaw, delta, matrices, buffers, light);
    }
}
