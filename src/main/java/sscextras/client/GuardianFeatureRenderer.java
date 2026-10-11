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
        var state = RuneboundGuardian.state(player);
        var client = net.minecraft.client.MinecraftClient.getInstance();
        boolean statue = state.pedestalSleeping || state.deactivated;
        boolean hideHead = player == client.getCameraEntity() && client.options.getPerspective().isFirstPerson()
                && client.currentScreen == null;
        float sway = statue ? 0 : (float)Math.sin(age * .075f) * .12f + (float)Math.sin(limbAngle * .6662f) * Math.min(limbDistance, 1) * .06f;
        float lift = statue ? 0 : (float)Math.sin(age * .045f) * .025f;
        GuardianMesh.get(stage).render(matrices, buffers, light, statue ? null : getContextModel(),
                stage < 2 || state.energy > 0 && !state.deactivated, state.weatherTicks >= 72000, 1, null, GuardianEquipment.visuals(player),
                new GuardianMesh.Motion(stage == 3 && state.coreId != null || stage < 3, hideHead, sway, lift));
        if(statue) return;
        for(boolean left:new boolean[]{false,true}) {
            var stack=left==(player.getMainArm()==net.minecraft.util.Arm.LEFT)?player.getMainHandStack():player.getOffHandStack();
            if(stack.isEmpty())continue;
            matrices.push(); GuardianMesh.get(stage).hand(matrices,getContextModel(),left);
            net.minecraft.client.MinecraftClient.getInstance().getEntityRenderDispatcher().getHeldItemRenderer().renderItem(player,stack,
                    left?net.minecraft.client.render.model.json.ModelTransformationMode.THIRD_PERSON_LEFT_HAND:net.minecraft.client.render.model.json.ModelTransformationMode.THIRD_PERSON_RIGHT_HAND,
                    left,matrices,buffers,light);
            matrices.pop();
        }
    }
}
