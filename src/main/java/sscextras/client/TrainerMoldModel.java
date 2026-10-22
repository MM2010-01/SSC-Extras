package sscextras.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** Open rigid mold: articulated iron rails, a paw bed and a curved instep brace. */
public final class TrainerMoldModel {
    private static final Identifier TEXTURE = new Identifier("ssc-extras", "textures/entity/collar.png");
    private final ModelPart foot, brace;
    private final CuffModel band = new CuffModel(4.35f, 4.35f);
    public TrainerMoldModel() {
        var data = new ModelData();
        foot = data.getRoot().addChild("mold", ModelPartBuilder.create().uv(0,0)
            .cuboid(-2.65f,1.55f,-4.1f,5.3f,.55f,6.7f)
            .cuboid(-2.65f,-.45f,-2.8f,.45f,2,5.4f).cuboid(2.2f,-.45f,-2.8f,.45f,2,5.4f)
            .cuboid(-2.65f,-.45f,2.15f,5.3f,2,.45f),ModelTransform.NONE).createPart(32,32);
        brace = new ModelData().getRoot().addChild("brace", ModelPartBuilder.create().uv(0,0)
            .cuboid(-2.25f,-.1f,-2.45f,.4f,4.2f,.45f).cuboid(1.85f,-.1f,-2.45f,.4f,4.2f,.45f)
            .cuboid(-2.25f,-.1f,2,.4f,4.2f,.45f).cuboid(1.85f,-.1f,2,.4f,4.2f,.45f),ModelTransform.NONE).createPart(32,32);
    }
    public void foot(MatrixStack m, VertexConsumerProvider b, int light) { foot.render(m,b.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)),light,OverlayTexture.DEFAULT_UV,.72f,.76f,.82f,1); }
    public void brace(MatrixStack m, VertexConsumerProvider b, int light) { brace.render(m,b.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)),light,OverlayTexture.DEFAULT_UV,.72f,.76f,.82f,1); }
    public void band(MatrixStack m, VertexConsumerProvider b, int light) { band.render(m,b,light,false,0xb87ce8); }
}
