/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_1296
 *  net.minecraft.class_1297
 *  net.minecraft.class_1304
 *  net.minecraft.class_1799
 *  net.minecraft.class_4587
 *  net.minecraft.class_572
 *  net.minecraft.class_630
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 */
package mod.azure.azurelib.rewrite.render.armor;

import mod.azure.azurelib.cache.texture.AnimatableTexture;
import mod.azure.azurelib.rewrite.animation.AzAnimatorAccessor;
import mod.azure.azurelib.rewrite.model.AzBakedModel;
import mod.azure.azurelib.rewrite.model.AzBone;
import mod.azure.azurelib.rewrite.render.AzLayerRenderer;
import mod.azure.azurelib.rewrite.render.AzModelRenderer;
import mod.azure.azurelib.rewrite.render.AzRendererConfig;
import mod.azure.azurelib.rewrite.render.AzRendererPipeline;
import mod.azure.azurelib.rewrite.render.AzRendererPipelineContext;
import mod.azure.azurelib.rewrite.render.armor.AzArmorModel;
import mod.azure.azurelib.rewrite.render.armor.AzArmorModelRenderer;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRenderer;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRendererConfig;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRendererPipelineContext;
import mod.azure.azurelib.rewrite.render.armor.bone.AzArmorBoneContext;
import net.minecraft.class_1296;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_4587;
import net.minecraft.class_572;
import net.minecraft.class_630;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public class AzArmorRendererPipeline
extends AzRendererPipeline<class_1799> {
    private final AzArmorModel<?> armorModel;
    private final AzArmorRenderer armorRenderer;
    protected Matrix4f entityRenderTranslations = new Matrix4f();
    protected Matrix4f modelRenderTranslations = new Matrix4f();

    public AzArmorRendererPipeline(AzRendererConfig<class_1799> config, AzArmorRenderer armorRenderer) {
        super(config);
        this.armorModel = new AzArmorModel(this);
        this.armorRenderer = armorRenderer;
    }

    @Override
    protected AzRendererPipelineContext<class_1799> createContext(AzRendererPipeline<class_1799> rendererPipeline) {
        return new AzArmorRendererPipelineContext(rendererPipeline);
    }

    @Override
    protected AzModelRenderer<class_1799> createModelRenderer(AzLayerRenderer<class_1799> layerRenderer) {
        return new AzArmorModelRenderer(this, layerRenderer);
    }

    @Override
    protected AzLayerRenderer<class_1799> createLayerRenderer(AzRendererConfig<class_1799> config) {
        return new AzLayerRenderer<class_1799>(config::renderLayers);
    }

    @Override
    protected void updateAnimatedTextureFrame(class_1799 animatable) {
        class_1297 currentEntity = this.context().currentEntity();
        if (currentEntity != null) {
            AnimatableTexture.setAndUpdate(this.config.textureLocation(animatable));
        }
    }

    @Override
    public void preRender(AzRendererPipelineContext<class_1799> context, boolean isReRender) {
        AzArmorRendererPipelineContext armorContext = (AzArmorRendererPipelineContext)context;
        class_572<?> baseModel = armorContext.baseModel();
        AzArmorBoneContext boneContext = armorContext.boneContext();
        AzArmorRendererConfig config = this.config();
        class_1304 currentSlot = armorContext.currentSlot();
        float scaleWidth = config.scaleWidth(context.animatable());
        float scaleHeight = config.scaleHeight(context.animatable());
        class_1799 animatable = (class_1799)armorContext.animatable();
        AzBakedModel model = this.armorRenderer.provider().provideBakedModel(animatable);
        class_4587 poseStack = armorContext.poseStack();
        this.entityRenderTranslations = new Matrix4f((Matrix4fc)poseStack.method_23760().method_23761());
        this.armorModel.applyBaseModel(baseModel);
        boneContext.grabRelevantBones(model, config.boneProvider());
        boneContext.applyBaseTransformations(baseModel);
        this.scaleModelForBaby(armorContext, isReRender);
        this.scaleModelForRender(context, scaleWidth, scaleHeight, isReRender);
        this.scaleBoneWithModelPart(armorContext, boneContext, isReRender);
        if (AzAnimatorAccessor.getOrNull(this.context().currentEntity()) == null) {
            boneContext.applyBoneVisibilityBySlot(currentSlot);
        }
        if (config.alpha(context.animatable()) < 1.0f) {
            armorContext.setAlpha(config.alpha(context.animatable()));
            armorContext.setTranslucent(true);
        }
        config.preRenderEntry(context);
    }

    @Override
    public void postRender(AzRendererPipelineContext<class_1799> context, boolean isReRender) {
        this.config.postRenderEntry(context);
    }

    public void scaleBoneWithModelPart(AzArmorRendererPipelineContext context, AzArmorBoneContext boneContext, boolean isReRender) {
        class_572<?> baseModel = context.baseModel();
        class_1304 currentSlot = context.currentSlot();
        if (isReRender) {
            return;
        }
        switch (currentSlot) {
            case field_6169: {
                this.setBoneScale(boneContext.head, baseModel.field_3398);
                break;
            }
            case field_6174: {
                this.setBoneScale(boneContext.leftArm, baseModel.field_27433);
                this.setBoneScale(boneContext.rightArm, baseModel.field_3401);
                this.setBoneScale(boneContext.body, baseModel.field_3391);
                break;
            }
            case field_6166: 
            case field_6172: {
                this.setBoneScale(boneContext.leftLeg, baseModel.field_3397);
                this.setBoneScale(boneContext.rightLeg, baseModel.field_3392);
            }
        }
    }

    private void setBoneScale(AzBone bone, class_630 modelPart) {
        bone.setScaleX(modelPart.field_37938);
        bone.setScaleY(modelPart.field_37939);
        bone.setScaleZ(modelPart.field_37940);
    }

    public void scaleModelForBaby(AzArmorRendererPipelineContext context, boolean isReRender) {
        class_1296 ageableMob;
        class_1297 currentEntity = context.currentEntity();
        if (!(currentEntity instanceof class_1296) || !(ageableMob = (class_1296)currentEntity).method_6109() || isReRender) {
            return;
        }
        class_572<?> baseModel = context.baseModel();
        class_1304 currentSlot = context.currentSlot();
        class_4587 poseStack = context.poseStack();
        if (currentSlot == class_1304.field_6169) {
            if (baseModel.field_20915) {
                float headScale = 1.5f / baseModel.field_20918;
                poseStack.method_22905(headScale, headScale, headScale);
            }
            poseStack.method_46416(0.0f, baseModel.field_20916 / 16.0f, baseModel.field_20917 / 16.0f);
        } else {
            float bodyScale = 1.0f / baseModel.field_20919;
            poseStack.method_22905(bodyScale, bodyScale, bodyScale);
            poseStack.method_46416(0.0f, baseModel.field_20920 / 16.0f, 0.0f);
        }
    }

    public AzArmorModel<?> armorModel() {
        return this.armorModel;
    }

    public AzArmorRendererConfig config() {
        return (AzArmorRendererConfig)super.config();
    }

    public AzArmorRendererPipelineContext context() {
        return (AzArmorRendererPipelineContext)super.context();
    }

    public AzArmorRenderer renderer() {
        return this.armorRenderer;
    }
}

