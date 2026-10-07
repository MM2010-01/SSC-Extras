package sscextras.client;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PillagerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.IllagerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.util.Identifier;

public final class MountMerchantRenderer extends PillagerEntityRenderer {
    public MountMerchantRenderer(EntityRendererFactory.Context context) { super(context); addFeature(new Hood(this)); }
    private static final class Hood extends FeatureRenderer<PillagerEntity, IllagerEntityModel<PillagerEntity>> {
        private static final Identifier CLOTH = new Identifier("minecraft", "textures/block/brown_wool.png");
        private final ModelPart head, body;
        Hood(MountMerchantRenderer renderer) {
            super(renderer); var data = new ModelData(); var root = data.getRoot();
            root.addChild("hood", ModelPartBuilder.create().uv(0, 0)
                    .cuboid(-5, -11, -4.5f, 10, 2, 9)
                    .cuboid(-5, -9, -4.5f, 1, 10, 9).cuboid(4, -9, -4.5f, 1, 10, 9)
                    .cuboid(-4, -9, 4, 8, 10, 1), ModelTransform.NONE);
            root.addChild("coat", ModelPartBuilder.create().uv(0, 0)
                    .cuboid(-5.25f, 0, -3.75f, 10.5f, 17, 7.5f).cuboid(-6, -.1f, -3.9f, 12, 3, 7.8f), ModelTransform.NONE);
            var parts = TexturedModelData.of(data, 16, 16).createModel(); head = parts.getChild("hood"); body = parts.getChild("coat");
        }
        @Override public void render(MatrixStack matrices, VertexConsumerProvider consumers, int light, PillagerEntity entity,
                float limbAngle, float limbDistance, float delta, float progress, float yaw, float pitch) {
            if (entity.isInvisible()) return;
            head.copyTransform(getContextModel().getHead()); body.copyTransform(getContextModel().getPart().getChild("body"));
            var vertices = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(CLOTH));
            int overlay = net.minecraft.client.render.entity.LivingEntityRenderer.getOverlay(entity, 0);
            head.render(matrices, vertices, light, overlay); body.render(matrices, vertices, light, overlay);
        }
    }
}
