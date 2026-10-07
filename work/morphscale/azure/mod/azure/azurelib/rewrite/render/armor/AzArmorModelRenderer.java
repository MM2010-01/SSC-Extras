/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_1799
 *  net.minecraft.class_4587
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.joml.Vector3f
 */
package mod.azure.azurelib.rewrite.render.armor;

import mod.azure.azurelib.rewrite.animation.impl.AzItemAnimator;
import mod.azure.azurelib.rewrite.model.AzBone;
import mod.azure.azurelib.rewrite.render.AzLayerRenderer;
import mod.azure.azurelib.rewrite.render.AzModelRenderer;
import mod.azure.azurelib.rewrite.render.AzRendererPipelineContext;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRendererPipeline;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRendererPipelineContext;
import mod.azure.azurelib.util.RenderUtils;
import net.minecraft.class_1799;
import net.minecraft.class_4587;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public class AzArmorModelRenderer
extends AzModelRenderer<class_1799> {
    protected final AzArmorRendererPipeline armorRendererPipeline;

    public AzArmorModelRenderer(AzArmorRendererPipeline armorRendererPipeline, AzLayerRenderer<class_1799> layerRenderer) {
        super(armorRendererPipeline, layerRenderer);
        this.armorRendererPipeline = armorRendererPipeline;
    }

    @Override
    public void render(AzRendererPipelineContext<class_1799> context, boolean isReRender) {
        class_4587 poseStack = context.poseStack();
        poseStack.method_22903();
        poseStack.method_46416(0.0f, 1.5f, 0.0f);
        poseStack.method_22905(-1.0f, -1.0f, 1.0f);
        if (!isReRender) {
            class_1799 animatable = context.animatable();
            AzItemAnimator animator = this.armorRendererPipeline.renderer().animator();
            if (animator != null) {
                this.handleAnimation(animator, animatable, context.partialTick());
            }
        }
        this.armorRendererPipeline.modelRenderTranslations = new Matrix4f((Matrix4fc)poseStack.method_23760().method_23761());
        super.render(context, isReRender);
        poseStack.method_22909();
    }

    @Override
    public void renderRecursively(AzRendererPipelineContext<class_1799> context, AzBone bone, boolean isReRender) {
        class_4587 poseStack = context.poseStack();
        AzArmorRendererPipelineContext ctx = this.armorRendererPipeline.context();
        poseStack.method_22903();
        if (bone.isTrackingMatrices()) {
            Matrix4f poseState = new Matrix4f((Matrix4fc)poseStack.method_23760().method_23761());
            Matrix4f localMatrix = RenderUtils.invertAndMultiplyMatrices(poseState, this.armorRendererPipeline.entityRenderTranslations);
            bone.setModelSpaceMatrix(RenderUtils.invertAndMultiplyMatrices(poseState, this.armorRendererPipeline.modelRenderTranslations));
            bone.setLocalSpaceMatrix(RenderUtils.translateMatrix(localMatrix, new Vector3f()));
            bone.setWorldSpaceMatrix(RenderUtils.translateMatrix(new Matrix4f((Matrix4fc)localMatrix), ctx.currentEntity().method_19538().method_46409()));
        }
        context.setVertexConsumer(this.getOrRefreshRenderBuffer(isReRender, context));
        super.renderRecursively(context, bone, isReRender);
        poseStack.method_22909();
    }
}

