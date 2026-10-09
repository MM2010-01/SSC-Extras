package sscextras.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import sscextras.guardian.GuardianEquipment;

public final class GuardianEquipmentRenderer {
    private GuardianEquipmentRenderer() { }
    public static void original(AbstractClientPlayerEntity player, PlayerEntityModel<?> model, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        int equipment = GuardianEquipment.visuals(player);
        if ((equipment & 1) != 0) {
            limb(player, model.leftArm, true, true, matrices, buffers, light);
            limb(player, model.rightArm, false, true, matrices, buffers, light);
        }
        if ((equipment & 2) != 0) {
            limb(player, model.leftLeg, true, false, matrices, buffers, light);
            limb(player, model.rightLeg, false, false, matrices, buffers, light);
        }
        if ((equipment & 4) != 0) {
            matrices.push(); model.body.rotate(matrices);
            GuardianAdornmentMesh.render("original/collar", matrices, buffers, light);
            matrices.pop();
        }
    }
    public static void limb(AbstractClientPlayerEntity player, ModelPart part, boolean left, boolean hand,
            MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        boolean slim = hand && player.getModel().equals("slim");
        matrices.push(); part.rotate(matrices);
        GuardianAdornmentMesh.render((slim ? "slim/" : "original/") + (left ? "left_" : "right_") + (hand ? "hand" : "foot"), matrices, buffers, light);
        matrices.pop();
    }
}
