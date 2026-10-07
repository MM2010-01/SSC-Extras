/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_1297
 *  net.minecraft.class_1309
 *  net.minecraft.class_1799
 *  net.minecraft.class_1921
 *  net.minecraft.class_2960
 *  net.minecraft.class_310
 *  net.minecraft.class_4587
 *  net.minecraft.class_4588
 *  net.minecraft.class_4597
 *  net.minecraft.class_4597$class_4598
 *  net.minecraft.class_5602
 *  net.minecraft.class_572
 *  net.minecraft.class_918
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package mod.azure.azurelib.rewrite.render.armor;

import mod.azure.azurelib.rewrite.model.AzBakedModel;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRendererConfig;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRendererPipeline;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRendererPipelineContext;
import mod.azure.azurelib.rewrite.render.armor.bone.AzArmorBoneContext;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1799;
import net.minecraft.class_1921;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import net.minecraft.class_4597;
import net.minecraft.class_5602;
import net.minecraft.class_572;
import net.minecraft.class_918;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AzArmorModel<E extends class_1309>
extends class_572<E> {
    private final AzArmorRendererPipeline rendererPipeline;

    public AzArmorModel(AzArmorRendererPipeline rendererPipeline) {
        super(class_310.method_1551().method_31974().method_32072(class_5602.field_27579));
        this.rendererPipeline = rendererPipeline;
        this.field_3448 = false;
    }

    public void method_2828(@NotNull class_4587 poseStack, @Nullable class_4588 buffer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        boolean shouldOutline;
        class_310 mc = class_310.method_1551();
        AzArmorRendererPipelineContext context = this.rendererPipeline.context();
        class_1297 currentEntity = context.currentEntity();
        class_1799 currentStack = context.currentStack();
        class_4597.class_4598 bufferSource = class_310.method_1551().field_1769.field_20951.method_23000();
        boolean bl = shouldOutline = class_310.method_1551().field_1769.method_3270() && mc.method_27022(currentEntity);
        if (shouldOutline) {
            bufferSource = class_310.method_1551().field_1769.field_20951.method_23003();
        }
        AzArmorRendererConfig config = this.rendererPipeline.config();
        class_1799 animatable = (class_1799)context.animatable();
        float partialTick = mc.method_1488();
        class_2960 textureLocation = config.textureLocation(animatable);
        class_1921 renderType = context.getDefaultRenderType(animatable, textureLocation, (class_4597)bufferSource, partialTick);
        buffer = class_918.method_27952((class_4597)bufferSource, (class_1921)renderType, (boolean)false, (boolean)currentStack.method_7958());
        AzBakedModel model = this.rendererPipeline.renderer().provider().provideBakedModel(animatable);
        this.rendererPipeline.render(poseStack, model, animatable, (class_4597)bufferSource, null, buffer, 0.0f, partialTick, packedLight);
    }

    public void applyBaseModel(class_572<?> baseModel) {
        this.field_3448 = baseModel.field_3448;
        this.field_3400 = baseModel.field_3400;
        this.field_3449 = baseModel.field_3449;
        this.field_3395 = baseModel.field_3395;
        this.field_3399 = baseModel.field_3399;
    }

    public void method_2805(boolean pVisible) {
        super.method_2805(pVisible);
        AzArmorBoneContext boneContext = this.rendererPipeline.context().boneContext();
        boneContext.setAllVisible(pVisible);
    }
}

