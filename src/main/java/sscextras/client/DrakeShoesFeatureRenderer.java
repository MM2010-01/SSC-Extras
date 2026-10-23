package sscextras.client;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import sscextras.drake.DrakeShoes;

public final class DrakeShoesFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final DrakeShoeModel NORMAL = new DrakeShoeModel(-1), SLIM = new DrakeShoeModel(-1, true);

    public DrakeShoesFeatureRenderer(PlayerEntityRenderer renderer) { super(renderer); }

    private static boolean original(net.minecraft.entity.player.PlayerEntity player) {
        if (player.isInvisible() || player.isSpectator()) return false;
        var form = RegPlayerFormComponent.PLAYER_FORM.get(player).getCurrentForm();
        return form == RegPlayerForms.ORIGINAL_SHIFTER || form == RegPlayerForms.ORIGINAL_BEFORE_ENABLE;
    }

    static boolean supported(net.minecraft.entity.player.PlayerEntity player, boolean hand) {
        if (player.isInvisible() || player.isSpectator()) return false;
        int stage = sscextras.drake.EarthenDrake.stage(player);
        return original(player) || stage >= 0 && stage < 2 && TrainerLimbModel.active(player, hand);
    }

    @Override public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, AbstractClientPlayerEntity player,
            float limbAngle, float limbDistance, float tickDelta, float age, float yaw, float pitch) {
        boolean hands = supported(player, true) && DrakeShoes.visible(player, false);
        boolean feet = supported(player, false) && DrakeShoes.visible(player, true);
        if (!hands && !feet) return;
        boolean slim = player.getModel().equals("slim");
        var shoes = slim ? SLIM : NORMAL;
        var model = getContextModel();
        if (hands) {
            shoes.renderOriginalLimb(model.leftArm, true, true, slim, matrices, buffers, light);
            shoes.renderOriginalLimb(model.rightArm, false, true, slim, matrices, buffers, light);
        }
        if (feet) {
            shoes.renderOriginalLimb(model.leftLeg, true, false, slim, matrices, buffers, light);
            shoes.renderOriginalLimb(model.rightLeg, false, false, slim, matrices, buffers, light);
        }
    }

    public static void renderArm(AbstractClientPlayerEntity player, ModelPart arm, boolean left,
            MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (!supported(player, true) || !DrakeShoes.visible(player, false)) return;
        boolean slim = player.getModel().equals("slim");
        (slim ? SLIM : NORMAL).renderOriginalLimb(arm, left, true, slim, matrices, buffers, light);
    }
}
