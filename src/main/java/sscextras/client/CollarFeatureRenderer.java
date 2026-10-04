package sscextras.client;

import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import sscextras.collar.CollarSlots;
import sscextras.collar.Collars;
import sscextras.drake.EarthenDrake;

public final class CollarFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier TEXTURE = new Identifier("ssc-extras", "textures/entity/collar.png");
    private final ModelPart strap, buckle, charm;

    public CollarFeatureRenderer(PlayerEntityRenderer renderer) {
        super(renderer);
        ModelData model = new ModelData();
        var root = model.getRoot();
        root.addChild("strap", ModelPartBuilder.create()
                .cuboid(-4.3f, -0.9f, -4.3f, 8.6f, 1.5f, 0.55f)
                .cuboid(-4.3f, -0.9f, 3.75f, 8.6f, 1.5f, 0.55f)
                .cuboid(-4.3f, -0.9f, -3.75f, 0.55f, 1.5f, 7.5f)
                .cuboid(3.75f, -0.9f, -3.75f, 0.55f, 1.5f, 7.5f), ModelTransform.NONE);
        root.addChild("buckle", ModelPartBuilder.create()
                .cuboid(-1.25f, -1.05f, -4.55f, 2.5f, 0.3f, 0.3f)
                .cuboid(-1.25f, 0.55f, -4.55f, 2.5f, 0.3f, 0.3f)
                .cuboid(-1.25f, -0.75f, -4.55f, 0.3f, 1.3f, 0.3f)
                .cuboid(0.95f, -0.75f, -4.55f, 0.3f, 1.3f, 0.3f)
                .cuboid(-0.15f, -0.75f, -4.6f, 0.3f, 1.3f, 0.35f)
                .cuboid(-0.25f, 0.85f, -4.5f, 0.5f, 0.65f, 0.35f), ModelTransform.NONE);
        root.addChild("charm", ModelPartBuilder.create()
                .cuboid(-0.65f, 1.5f, -4.65f, 1.3f, 1.0f, 0.45f)
                .cuboid(-0.4f, 2.5f, -4.65f, 0.8f, 0.25f, 0.45f), ModelTransform.NONE);
        var baked = TexturedModelData.of(model, 32, 32).createModel();
        strap = baked.getChild("strap");
        buckle = baked.getChild("buckle");
        charm = baked.getChild("charm");
    }

    @Override public void render(MatrixStack matrices, VertexConsumerProvider vertices, int light,
            AbstractClientPlayerEntity player, float limbAngle, float limbDistance, float tickDelta,
            float animationProgress, float headYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator()) return;
        var collar = CollarSlots.visibleCollar(player);
        if (collar.isEmpty()) return;
        matrices.push();
        int drakeStage = EarthenDrake.stage(player);
        if (drakeStage == 3) matrices.translate(0, 0.66, -0.453333);
        getContextModel().head.rotate(matrices);
        matrices.translate(0, drakeStage == 2 ? 0.18 : drakeStage == 3 ? 0.14 : 1.0 / 16,
                drakeStage == 2 ? 0.09 : drakeStage == 3 ? 0.07 : 0);
        float scale = drakeStage == 2 ? 0.72f : drakeStage == 3 ? 0.65f : 0.8f;
        matrices.scale(scale, scale, scale);
        var buffer = vertices.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        boolean cursed = collar.isOf(Collars.CURSED);
        strap.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, cursed ? 0.49f : 0.78f,
                cursed ? 0.22f : 0.12f, cursed ? 0.72f : 0.19f, 1);
        buckle.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 0.95f, 0.76f, 0.36f, 1);
        charm.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 0.65f, 0.95f, 1, 1);
        matrices.pop();
    }
}
