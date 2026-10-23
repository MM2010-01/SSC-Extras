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
    private DrakeGearModel[] gear;
    private DrakeShoeModel[] shoes;
    private final DrakeGearModel nativeGear = new DrakeGearModel(-1);
    private final FormRenderer form;
    private int stage = -1;
    private boolean nativeSaddle;
    private boolean reins, saddle, chest, claws, vanillaSaddle, blinkers, blinkersClosed;
    private boolean handShoes, footShoes;
    public DrakeFormGearLayer(FormRenderer renderer) { super(renderer); form=renderer; }

    @Override public void preRender(MatrixStack matrices, FormAnimatable animatable, BakedGeoModel model, RenderLayer renderType,
            VertexConsumerProvider buffers, VertexConsumer buffer, float tickDelta, int light, int overlay) {
        stage = -1; nativeSaddle=false;
        var player = animatable.e;
        if (player == null || player.isInvisible() || player.isSpectator() || light == 0x7ffffffe) return;
        stage = EarthenDrake.stage(player);
        if (stage < 0) {
            nativeSaddle=DrakeRiding.quadruped(player)&&form.realModel.Hidden_Body&&DrakeEquipment.visible(player,DrakeEquipment.SADDLE);
            vanillaSaddle=DrakeEquipment.saddle(player).isOf(net.minecraft.item.Items.SADDLE);
            return;
        }
        if (gear == null) {
            gear = new DrakeGearModel[]{new DrakeGearModel(0), new DrakeGearModel(1), new DrakeGearModel(2), new DrakeGearModel(3)};
            shoes = new DrakeShoeModel[]{new DrakeShoeModel(0), new DrakeShoeModel(1), new DrakeShoeModel(2), new DrakeShoeModel(3)};
        }
        reins = DrakeEquipment.visible(player, DrakeEquipment.REINS);
        blinkers = DrakeEquipment.visible(player, DrakeEquipment.BLINDING_REIN);
        blinkersClosed = blinkers && BlindingRein.closed(DrakeEquipment.equipped(player, DrakeEquipment.BLINDING_REIN));
        saddle = DrakeEquipment.visible(player, DrakeEquipment.SADDLE);
        vanillaSaddle = DrakeEquipment.saddle(player).isOf(net.minecraft.item.Items.SADDLE);
        chest = stage == 3 && DrakeEquipment.visible(player, DrakeEquipment.RIDERS_CHEST);
        claws = stage == 3 && DrakeEquipment.visible(player, DrakeEquipment.CLAW_TIPS);
        // Reshaped early-stage limbs are drawn on the actual player paw joints.
        handShoes = DrakeShoes.visible(player, false) && !TrainerLimbModel.active(player, true);
        footShoes = DrakeShoes.visible(player, true) && !TrainerLimbModel.active(player, false);
    }

    @Override public void renderForBone(MatrixStack matrices, FormAnimatable animatable, GeoBone bone, RenderLayer renderType,
            VertexConsumerProvider buffers, VertexConsumer buffer, float tickDelta, int light, int overlay) {
        if(nativeSaddle && bone.getName().equals("bipedBody")) {
            nativeGear.render("bipedBody",matrices,buffers,light,false,true,false,false,vanillaSaddle);
            buffers.getBuffer(renderType);
        } else if (stage >= 0) {
            shoes[stage].captureGrip(animatable.e, bone.getName(), matrices);
            gear[stage].render(bone.getName(), matrices, buffers, light, reins, saddle, chest, claws, vanillaSaddle, blinkers, blinkersClosed);
            shoes[stage].render(bone.getName(), matrices, buffers, light, handShoes, footShoes);
            // AzureLib may retain the shared fallback buffer for the next body bone.
            buffers.getBuffer(renderType);
        }
    }
}
