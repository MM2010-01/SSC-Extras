package sscextras.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import sscextras.drake.*;

public final class DrakeGearFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final DrakeGearModel gear = new DrakeGearModel(-1), helmetGear = new DrakeGearModel(-1, true);
    public DrakeGearFeatureRenderer(PlayerEntityRenderer renderer) { super(renderer); }

    @Override public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, AbstractClientPlayerEntity player,
            float limbAngle, float limbDistance, float tickDelta, float age, float yaw, float pitch) {
        if (player.isInvisible() || player.isSpectator() || EarthenDrake.stage(player) >= 0) return;
        boolean reins = DrakeEquipment.visible(player, DrakeEquipment.REINS), saddle = DrakeEquipment.visible(player, DrakeEquipment.SADDLE);
        if (!reins && !saddle) return;
        matrices.push();
        getContextModel().head.rotate(matrices);
        matrices.scale(1, -1, -1);
        matrices.translate(0, -1.5, 0);
        var headGear = player.getEquippedStack(net.minecraft.entity.EquipmentSlot.HEAD).isEmpty() ? gear : helmetGear;
        headGear.render("bipedHead", matrices, buffers, light, reins, false, false, false, false,
                DrakeEquipment.visible(player, DrakeEquipment.BLINDING_REIN),
                BlindingRein.closed(DrakeEquipment.equipped(player, DrakeEquipment.BLINDING_REIN)));
        matrices.pop();
        if (!(getContextModel() instanceof AuthoredPlayerModel))
            for (var renderer : net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderUtils.getPlayerAllFormRenderer(player))
                if (DrakeRiding.quadruped(player) && renderer.realModel.Hidden_Body) saddle=false;
        if (saddle && DrakeRiding.quadruped(player) && authoredSaddle(matrices, buffers, light, player)) return;
        matrices.push();
        getContextModel().body.rotate(matrices);
        matrices.scale(1, -1, -1);
        matrices.translate(0, -1.5, 0);
        gear.render("bipedBody", matrices, buffers, light, false, saddle, false, false,
                DrakeEquipment.saddle(player).isOf(net.minecraft.item.Items.SADDLE));
        matrices.pop();
    }

    private boolean authoredSaddle(MatrixStack matrices, VertexConsumerProvider buffers, int light, AbstractClientPlayerEntity player) {
        org.joml.Vector3f[] box;
        var model = getContextModel();
        if (model instanceof GuardianPlayerModel guardian) box = guardian.mesh().bounds("bipedBody");
        else if (model instanceof DrakeGolemPlayerModel golem) box = RiggedMesh.bounds(golem.mesh().bones.get("bipedBody").parts());
        else return false;
        if (box == null) return false;
        var center = new org.joml.Vector3f(box[0]).add(box[1]).mul(.5f);
        var size = new org.joml.Vector3f(box[1]).sub(box[0]).mul(16);
        float padding=0;
        matrices.push();
        if (model instanceof GuardianPlayerModel guardian) {
            guardian.transform(matrices, "bipedBody");
            matrices.translate(center.x, center.y, box[1].z);
            matrices.multiply(net.minecraft.util.math.RotationAxis.NEGATIVE_X.rotationDegrees(90));
            matrices.scale((size.x + .35f) / 8, size.z / 4, size.y * .65f / 7);
        } else if (model instanceof DrakeGolemPlayerModel golem) {
            golem.transform(matrices, "bipedBody");
            matrices.translate(center.x, box[0].y, center.z);
            matrices.scale((size.x + .35f) / 8, size.y / 4, size.z * .65f / 7);
            // This authored torso's first part is its load-bearing shell; dorsal plates remain in the center channel.
            var shell=RiggedMesh.bounds(java.util.List.of(golem.mesh().bones.get("bipedBody").parts().get(0)));
            padding=(shell[0].y-box[0].y)*16*4/size.y;
        }
        gear.renderBodySaddle(matrices, buffers, light, DrakeEquipment.saddle(player).isOf(net.minecraft.item.Items.SADDLE),padding);
        matrices.pop();
        return true;
    }
}
