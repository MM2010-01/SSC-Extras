package sscextras.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Open rigid mold: articulated iron rails, a padded paw bed and engraved cuffs. */
public final class TrainerMoldModel {
    private static final Identifier TEXTURE = new Identifier("ssc-extras", "textures/entity/collar.png");
    private final ModelPart foot, pads, rod;
    private final CuffModel band = new CuffModel(4.35f, 4.35f);
    public TrainerMoldModel() {
        rod = new ModelData().getRoot().addChild("rod", ModelPartBuilder.create().uv(0,0)
                .cuboid(-.22f,-.06f,-.22f,.44f,1.12f,.44f),ModelTransform.NONE).createPart(32,32);
        var data = new ModelData();
        foot = data.getRoot().addChild("mold", ModelPartBuilder.create().uv(0,0)
            .cuboid(-2.65f,1.55f,-4.1f,5.3f,.55f,6.7f)
            .cuboid(-2.65f,-.45f,-2.8f,.45f,2,5.4f).cuboid(2.2f,-.45f,-2.8f,.45f,2,5.4f)
            .cuboid(-2.65f,-.45f,2.15f,5.3f,2,.45f)
            .cuboid(-.75f,.15f,-4.1f,.18f,1.4f,2.1f).cuboid(.57f,.15f,-4.1f,.18f,1.4f,2.1f),ModelTransform.NONE).createPart(32,32);
        pads = new ModelData().getRoot().addChild("pads", ModelPartBuilder.create().uv(0,0)
            .cuboid(-1.4f,2.1f,-1.5f,2.8f,.4f,2.9f)
            .cuboid(-1.94f,2.1f,-3.7f,.9f,.4f,1.4f).cuboid(-.45f,2.1f,-3.9f,.9f,.4f,1.4f)
            .cuboid(1.04f,2.1f,-3.7f,.9f,.4f,1.4f),ModelTransform.NONE).createPart(32,32);
    }
    public void foot(MatrixStack m, VertexConsumerProvider b, int light) {
        var vertex = b.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        foot.render(m,vertex,light,OverlayTexture.DEFAULT_UV,.72f,.76f,.82f,1);
        pads.render(m,vertex,light,OverlayTexture.DEFAULT_UV,.23f,.14f,.26f,1);
    }
    /** Join two posed frame points in model pixels; shared endpoints keep every hinge closed. */
    public void rod(MatrixStack m, VertexConsumerProvider b, int light, org.joml.Vector3f from, org.joml.Vector3f to) {
        var direction = new org.joml.Vector3f(to).sub(from);
        float length = direction.length();
        if (length < .001f) return;
        m.push(); m.translate(from.x/16,from.y/16,from.z/16);
        m.multiply(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0,1,0),direction.div(length)));
        m.scale(1,length,1);
        rod.render(m,b.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)),light,OverlayTexture.DEFAULT_UV,.72f,.76f,.82f,1);
        m.pop();
    }
    public void band(MatrixStack m, VertexConsumerProvider b, int light) { band.render(m,b,light,false,0xb87ce8); }
    /** Current frame cuff: fitted inner width/depth, with 1.52-pixel physical height. */
    public void fittedBand(MatrixStack m, VertexConsumerProvider b, int light, float width, float depth, float lengthScale) {
        m.scale(width/4.35f,.8f/lengthScale,depth/4.35f);
        band(m,b,light);
    }
}
