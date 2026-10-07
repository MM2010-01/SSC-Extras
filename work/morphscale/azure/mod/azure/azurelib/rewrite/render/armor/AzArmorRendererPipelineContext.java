/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_1297
 *  net.minecraft.class_1304
 *  net.minecraft.class_1792
 *  net.minecraft.class_1799
 *  net.minecraft.class_1921
 *  net.minecraft.class_2960
 *  net.minecraft.class_4057
 *  net.minecraft.class_4597
 *  net.minecraft.class_572
 *  org.jetbrains.annotations.NotNull
 *  org.jetbrains.annotations.Nullable
 */
package mod.azure.azurelib.rewrite.render.armor;

import mod.azure.azurelib.core.object.Color;
import mod.azure.azurelib.rewrite.render.AzRendererPipeline;
import mod.azure.azurelib.rewrite.render.AzRendererPipelineContext;
import mod.azure.azurelib.rewrite.render.armor.bone.AzArmorBoneContext;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1921;
import net.minecraft.class_2960;
import net.minecraft.class_4057;
import net.minecraft.class_4597;
import net.minecraft.class_572;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AzArmorRendererPipelineContext
extends AzRendererPipelineContext<class_1799> {
    private final AzArmorBoneContext boneContext = new AzArmorBoneContext();
    private class_572<?> baseModel = null;
    private class_1297 currentEntity = null;
    private class_1304 currentSlot = null;
    private class_1799 currentStack = null;
    private boolean translucent = false;

    public AzArmorRendererPipelineContext(AzRendererPipeline<class_1799> rendererPipeline) {
        super(rendererPipeline);
    }

    @Override
    @NotNull
    public class_1921 getDefaultRenderType(class_1799 animatable, class_2960 texture, @Nullable class_4597 bufferSource, float partialTick) {
        return this.translucent ? class_1921.method_29379((class_2960)texture) : class_1921.method_25448((class_2960)texture);
    }

    public void prepare(@Nullable class_1297 entity, class_1799 stack, @Nullable class_1304 slot, @Nullable class_572<?> baseModel) {
        this.baseModel = baseModel;
        this.currentEntity = entity;
        this.currentStack = stack;
        this.animatable = stack;
        this.currentSlot = slot;
    }

    public void setTranslucent(boolean translucent) {
        this.translucent = translucent;
    }

    @Override
    public Color getRenderColor(class_1799 animatable, float partialTick, int packedLight) {
        Color color;
        class_1792 class_17922 = this.currentStack.method_7909();
        if (class_17922 instanceof class_4057) {
            class_4057 dyeableArmorItem = (class_4057)class_17922;
            color = Color.ofOpaque(dyeableArmorItem.method_7800(animatable));
        } else {
            color = Color.WHITE;
        }
        return color;
    }

    public class_572<?> baseModel() {
        return this.baseModel;
    }

    public AzArmorBoneContext boneContext() {
        return this.boneContext;
    }

    public class_1297 currentEntity() {
        return this.currentEntity;
    }

    public class_1304 currentSlot() {
        return this.currentSlot;
    }

    public class_1799 currentStack() {
        return this.currentStack;
    }
}

