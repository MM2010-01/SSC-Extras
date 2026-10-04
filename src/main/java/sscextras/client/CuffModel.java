package sscextras.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** A closed iron band, raised rims and an inset moondust seal. Dimensions are model pixels. */
public final class CuffModel {
    private static final Identifier TEXTURE = new Identifier("ssc-extras", "textures/entity/collar.png");
    private final ModelPart band, rims, seal;

    public CuffModel(float width, float depth) {
        var data = new ModelData();
        var root = data.getRoot();
        var body = ModelPartBuilder.create();
        ring(body, width, depth, -.8f, 1.6f, .3f);
        root.addChild("band", body, ModelTransform.NONE);
        var edges = ModelPartBuilder.create();
        ring(edges, width + .08f, depth + .08f, -.95f, .28f, .32f);
        ring(edges, width + .08f, depth + .08f, .67f, .28f, .32f);
        edges.cuboid(-.6f, -.65f, -depth / 2 - .46f, 1.2f, 1.3f, .22f);
        root.addChild("rims", edges, ModelTransform.NONE);
        root.addChild("seal", ModelPartBuilder.create().cuboid(-.35f, -.4f, -depth / 2 - .48f, .7f, .8f, .24f), ModelTransform.NONE);
        var baked = TexturedModelData.of(data, 32, 32).createModel();
        band = baked.getChild("band"); rims = baked.getChild("rims"); seal = baked.getChild("seal");
    }

    private static void ring(ModelPartBuilder model, float width, float depth, float y, float height, float thick) {
        float x = width / 2, z = depth / 2;
        model.cuboid(-x - thick, y, -z - thick, width + 2 * thick, height, thick)
                .cuboid(-x - thick, y, z, width + 2 * thick, height, thick)
                .cuboid(-x - thick, y, -z, thick, height, depth)
                .cuboid(x, y, -z, thick, height, depth);
    }

    public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        var buffer = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        band.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, .57f, .62f, .67f, 1);
        rims.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, .88f, .92f, .94f, 1);
        seal.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, .45f, .93f, .88f, 1);
    }
}
