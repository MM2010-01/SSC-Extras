package sscextras.client;

import mod.azure.azurelib.cache.object.GeoBone;
import mod.azure.azurelib.cache.object.BakedGeoModel;
import mod.azure.azurelib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormAnimatable;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import sscextras.drake.*;

public final class DrakeFormGearLayer extends GeoRenderLayer<FormAnimatable> {
    private final DrakeGearModel[] gear = {new DrakeGearModel(0), new DrakeGearModel(1), new DrakeGearModel(2), new DrakeGearModel(3)};
    private int stage = -1;
    private boolean reins, saddle, chest, claws, vanillaSaddle;
    public DrakeFormGearLayer(FormRenderer renderer) { super(renderer); }

    @Override public void preRender(MatrixStack matrices, FormAnimatable animatable, BakedGeoModel model, RenderLayer renderType,
            VertexConsumerProvider buffers, VertexConsumer buffer, float tickDelta, int light, int overlay) {
        stage = -1;
        var player = animatable.e;
        if (player == null || player.isInvisible() || player.isSpectator() || light == 0x7ffffffe) return;
        stage = EarthenDrake.stage(player);
        if (stage < 0) return;
        reins = DrakeEquipment.visible(player, DrakeEquipment.REINS);
        saddle = DrakeEquipment.visible(player, DrakeEquipment.SADDLE);
        vanillaSaddle = DrakeEquipment.saddle(player).isOf(net.minecraft.item.Items.SADDLE);
        chest = stage == 3 && DrakeEquipment.visible(player, DrakeEquipment.RIDERS_CHEST);
        claws = stage == 3 && DrakeEquipment.visible(player, DrakeEquipment.CLAW_TIPS);
    }

    @Override public void renderForBone(MatrixStack matrices, FormAnimatable animatable, GeoBone bone, RenderLayer renderType,
            VertexConsumerProvider buffers, VertexConsumer buffer, float tickDelta, int light, int overlay) {
        if (stage >= 0) {
            gear[stage].render(bone.getName(), matrices, buffers, light, reins, saddle, chest, claws, vanillaSaddle);
            // AzureLib may retain the shared fallback buffer for the next body bone.
            buffers.getBuffer(renderType);
        }
    }
}
