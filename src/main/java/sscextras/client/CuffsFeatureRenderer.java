package sscextras.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderUtils;
import sscextras.cuffs.MetalCuffs;

public final class CuffsFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final CuffModel normal = new CuffModel(4.15f, 4.15f), slim = new CuffModel(3.15f, 4.15f), armored = new CuffModel(6.15f, 6.15f);
    public CuffsFeatureRenderer(PlayerEntityRenderer renderer) { super(renderer); }

    @Override public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, AbstractClientPlayerEntity player,
            float limbAngle, float limbDistance, float tickDelta, float age, float yaw, float pitch) {
        if (player.isInvisible() || player.isSpectator()) return;
        boolean wrists = !MetalCuffs.equipped(player, false, true).isEmpty(), ankles = !MetalCuffs.equipped(player, true, true).isEmpty();
        if (!wrists && !ankles) return;
        boolean leftArm = true, rightArm = true, leftLeg = true, rightLeg = true;
        for (var renderer : FormRenderUtils.getPlayerAllFormRenderer(player)) {
            leftArm &= !renderer.realModel.Hidden_LeftArm; rightArm &= !renderer.realModel.Hidden_RightArm;
            leftLeg &= !renderer.realModel.Hidden_LeftLeg; rightLeg &= !renderer.realModel.Hidden_RightLeg;
        }
        boolean thin = player.getModel().equals("slim");
        boolean boots = !player.getEquippedStack(net.minecraft.entity.EquipmentSlot.FEET).isEmpty();
        CuffModel wristModel = thin ? slim : normal;
        float wristX = thin ? .5f : 1;
        var model = getContextModel();
        if (wrists && leftArm) renderLimb(matrices, buffers, light, model.leftArm, wristModel, wristX, 7.6f);
        if (wrists && rightArm) renderLimb(matrices, buffers, light, model.rightArm, wristModel, -wristX, 7.6f);
        if (ankles && leftLeg) renderLimb(matrices, buffers, light, model.leftLeg, boots ? armored : normal, 0, 9.6f);
        if (ankles && rightLeg) renderLimb(matrices, buffers, light, model.rightLeg, boots ? armored : normal, 0, 9.6f);
    }

    private static void renderLimb(MatrixStack matrices, VertexConsumerProvider buffers, int light, ModelPart limb, CuffModel cuff, float x, float y) {
        matrices.push();
        limb.rotate(matrices);
        matrices.translate(x / 16, y / 16, 0);
        cuff.render(matrices, buffers, light, false);
        matrices.pop();
    }
}
