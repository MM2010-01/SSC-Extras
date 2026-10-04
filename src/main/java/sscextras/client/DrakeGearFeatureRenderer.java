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
        headGear.render("bipedHead", matrices, buffers, light, reins, false, false, false);
        matrices.pop();
        matrices.push();
        getContextModel().body.rotate(matrices);
        matrices.scale(1, -1, -1);
        matrices.translate(0, -1.5, 0);
        gear.render("bipedBody", matrices, buffers, light, false, saddle, false, false,
                DrakeEquipment.saddle(player).isOf(net.minecraft.item.Items.SADDLE));
        matrices.pop();
    }
}
