package sscextras.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** One thick hollow support around the neck, tilted 45 degrees with the head. */
public final class TrainerNeckModel {
    private static final Identifier TEXTURE = new Identifier("ssc-extras", "textures/entity/collar.png");
    private final ModelPart support = new ModelData().getRoot().addChild("support", ModelPartBuilder.create().uv(0,0)
            .cuboid(-3,-2,-3,6,4,.65f).cuboid(-3,-2,2.35f,6,4,.65f)
            .cuboid(-3,-2,-2.35f,.65f,4,4.7f).cuboid(2.35f,-2,-2.35f,.65f,4,4.7f),
            ModelTransform.NONE).createPart(32,32);
    public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        matrices.push(); matrices.translate(0,.25/16,2.5/16);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(45));
        support.render(matrices,buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)),light,OverlayTexture.DEFAULT_UV,.72f,.76f,.82f,1);
        matrices.pop();
    }
}
