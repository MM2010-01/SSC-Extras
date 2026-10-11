package sscextras.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import sscextras.guardian.*;

public final class GuardianFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    public GuardianFeatureRenderer(PlayerEntityRenderer renderer) { super(renderer); }
    @Override public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, AbstractClientPlayerEntity player,
            float limbAngle, float limbDistance, float delta, float age, float headYaw, float headPitch) {
        int stage = RuneboundGuardian.stage(player);
        if (player.isInvisible() || RuneboundGuardian.state(player).detached) return;
        if (stage < 0) {
            if (net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager.getForm(player)
                    == net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.ORIGINAL_SHIFTER)
                GuardianEquipmentRenderer.original(player, getContextModel(), matrices, buffers, light);
            return;
        }
    }
}
