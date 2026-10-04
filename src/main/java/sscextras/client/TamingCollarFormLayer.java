package sscextras.client;

import mod.azure.azurelib.cache.object.BakedGeoModel;
import mod.azure.azurelib.cache.object.GeoBone;
import mod.azure.azurelib.renderer.layer.GeoRenderLayer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormAnimatable;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import sscextras.collar.CollarSlots;
import sscextras.collar.Collars;
import sscextras.drake.EarthenDrake;

public final class TamingCollarFormLayer extends GeoRenderLayer<FormAnimatable> {
    private final CuffModel early = new CuffModel(7, 5.5f, true), second = new CuffModel(8.2f, 8.2f, true),
            mature = new CuffModel(4.6f, 4.4f, true);
    private int stage = -1;
    public TamingCollarFormLayer(FormRenderer renderer) { super(renderer); }

    @Override public void preRender(MatrixStack matrices, FormAnimatable animatable, BakedGeoModel model, RenderLayer renderType,
            VertexConsumerProvider buffers, VertexConsumer buffer, float tickDelta, int light, int overlay) {
        stage = -1;
        var player = animatable.e;
        if (player == null || player.isInvisible() || player.isSpectator() || light == 0x7ffffffe) return;
        if (CollarSlots.visibleCollar(player).isOf(Collars.TAMING)) stage = EarthenDrake.stage(player);
    }

    @Override public void renderForBone(MatrixStack matrices, FormAnimatable animatable, GeoBone bone, RenderLayer renderType,
            VertexConsumerProvider buffers, VertexConsumer buffer, float tickDelta, int light, int overlay) {
        if (stage < 0 || bone.isHidden() || !(stage == 3 ? bone.getName().equals("neck") : bone.getName().equals("bipedHead"))) return;
        matrices.push();
        if (stage == 3) {
            matrices.translate(0, 11.55 / 16, 5.25 / 16);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(35));
        } else {
            matrices.translate(0, (stage == 2 ? 24 : 23.65) / 16, 0);
        }
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
        (stage == 3 ? mature : stage == 2 ? second : early).render(matrices, buffers, light, true);
        matrices.pop();
        buffers.getBuffer(renderType);
    }
}
