package sscextras.client;

import mod.azure.azurelib.cache.object.BakedGeoModel;
import mod.azure.azurelib.cache.object.GeoBone;
import mod.azure.azurelib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormAnimatable;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import sscextras.cuffs.MetalCuffs;
import sscextras.drake.EarthenDrake;

public final class CuffsFormLayer extends GeoRenderLayer<FormAnimatable> {
    private final FormRenderer form;
    private final CuffModel normal = new CuffModel(4.15f, 4.15f), drakeWrist = new CuffModel(4.9f, 4.8f),
            drakeAnkle = new CuffModel(3.55f, 3.15f), mature = new CuffModel(2.35f, 2.35f);
    private boolean wrists, ankles;
    private int stage;

    public CuffsFormLayer(FormRenderer renderer) { super(renderer); form = renderer; }

    @Override public void preRender(MatrixStack matrices, FormAnimatable animatable, BakedGeoModel model, RenderLayer renderType,
            VertexConsumerProvider buffers, VertexConsumer buffer, float tickDelta, int light, int overlay) {
        wrists = ankles = false;
        var player = animatable.e;
        if (player == null || player.isInvisible() || player.isSpectator() || light == 0x7ffffffe) return;
        stage = EarthenDrake.stage(player);
        wrists = !MetalCuffs.equipped(player, false, true).isEmpty();
        ankles = !MetalCuffs.equipped(player, true, true).isEmpty();
    }

    @Override public void renderForBone(MatrixStack matrices, FormAnimatable animatable, GeoBone bone, RenderLayer renderType,
            VertexConsumerProvider buffers, VertexConsumer buffer, float tickDelta, int light, int overlay) {
        if ((!wrists && !ankles) || bone.isHidden()) return;
        String name = bone.getName();
        boolean right = name.contains("Right") || name.startsWith("@left@");
        float side = right ? -1 : 1;
        CuffModel cuff;
        float x, y, z = 0;
        if (stage == 3) {
            if (wrists && (name.equals("left_front_paw") || name.equals("@left@_front_paw"))) {
                cuff = mature; x = side * 4.5653f; y = 1.5f; z = 4.2f;
            } else if (ankles && (name.equals("left_hind_paw") || name.equals("@left@_hind_paw"))) {
                cuff = mature; x = side * 4.1387f; y = 1.65f; z = -5.9f;
            } else return;
        } else if (wrists && (name.equals("bipedRightArm") && form.realModel.Hidden_RightArm
                || name.equals("bipedLeftArm") && form.realModel.Hidden_LeftArm)) {
            cuff = stage == 2 ? drakeWrist : normal;
            x = side * 6; y = 14.4f; z = stage == 2 ? .15f : 0;
        } else if (ankles && (name.equals("bipedRightLeg") && form.realModel.Hidden_RightLeg
                || name.equals("bipedLeftLeg") && form.realModel.Hidden_LeftLeg)) {
            cuff = stage == 2 ? drakeAnkle : normal;
            x = side * (stage == 2 ? 2.35f : 2); y = stage == 2 ? 2.3f : 2.4f;
            if (stage == 2) z = .55f;
        } else return;
        matrices.push();
        matrices.translate(x / 16, y / 16, z / 16);
        if (stage == 2 && name.contains("Leg")) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-56.888658f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
        cuff.render(matrices, buffers, light);
        matrices.pop();
        buffers.getBuffer(renderType);
    }
}
