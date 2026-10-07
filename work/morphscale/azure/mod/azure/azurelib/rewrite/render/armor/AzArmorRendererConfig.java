/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_1799
 *  net.minecraft.class_1921
 *  net.minecraft.class_2960
 *  org.jetbrains.annotations.Nullable
 */
package mod.azure.azurelib.rewrite.render.armor;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import mod.azure.azurelib.rewrite.animation.AzAnimator;
import mod.azure.azurelib.rewrite.render.AzRendererConfig;
import mod.azure.azurelib.rewrite.render.AzRendererPipelineContext;
import mod.azure.azurelib.rewrite.render.armor.bone.AzArmorBoneProvider;
import mod.azure.azurelib.rewrite.render.armor.bone.AzDefaultArmorBoneProvider;
import mod.azure.azurelib.rewrite.render.layer.AzRenderLayer;
import net.minecraft.class_1799;
import net.minecraft.class_1921;
import net.minecraft.class_2960;
import org.jetbrains.annotations.Nullable;

public class AzArmorRendererConfig
extends AzRendererConfig<class_1799> {
    private final AzArmorBoneProvider boneProvider;

    private AzArmorRendererConfig(Supplier<AzAnimator<class_1799>> animatorProvider, AzArmorBoneProvider boneProvider, Function<class_1799, class_2960> modelLocationProvider, Function<class_1799, class_1921> renderTypeProvider, List<AzRenderLayer<class_1799>> renderLayers, Function<AzRendererPipelineContext<class_1799>, AzRendererPipelineContext<class_1799>> preRenderEntry, Function<AzRendererPipelineContext<class_1799>, AzRendererPipelineContext<class_1799>> renderEntry, Function<AzRendererPipelineContext<class_1799>, AzRendererPipelineContext<class_1799>> postRenderEntry, Function<class_1799, class_2960> textureLocationProvider, Function<class_1799, Float> alphaFunction, Function<class_1799, Float> scaleHeight, Function<class_1799, Float> scaleWidth) {
        super(animatorProvider, modelLocationProvider, renderTypeProvider, renderLayers, preRenderEntry, renderEntry, postRenderEntry, textureLocationProvider, alphaFunction, scaleHeight, scaleWidth);
        this.boneProvider = boneProvider;
    }

    public AzArmorBoneProvider boneProvider() {
        return this.boneProvider;
    }

    public static Builder builder(class_2960 modelLocation, class_2960 textureLocation) {
        return new Builder($ -> modelLocation, $ -> textureLocation);
    }

    public static Builder builder(Function<class_1799, class_2960> modelLocationProvider, Function<class_1799, class_2960> textureLocationProvider) {
        return new Builder(modelLocationProvider, textureLocationProvider);
    }

    public static class Builder
    extends AzRendererConfig.Builder<class_1799> {
        private AzArmorBoneProvider boneProvider = new AzDefaultArmorBoneProvider();

        protected Builder(Function<class_1799, class_2960> modelLocationProvider, Function<class_1799, class_2960> textureLocationProvider) {
            super(modelLocationProvider, textureLocationProvider);
            this.renderTypeProvider = $ -> class_1921.method_25448((class_2960)((class_2960)textureLocationProvider.apply((class_1799)$)));
        }

        public Builder addRenderLayer(AzRenderLayer<class_1799> renderLayer) {
            return (Builder)super.addRenderLayer(renderLayer);
        }

        public Builder setRenderType(class_1921 renderType) {
            this.renderTypeProvider = $ -> renderType;
            return this;
        }

        public Builder setRenderType(Function<class_1799, class_1921> renderTypeProvider) {
            this.renderTypeProvider = renderTypeProvider;
            return this;
        }

        public Builder setAnimatorProvider(Supplier<@Nullable AzAnimator<class_1799>> animatorProvider) {
            return (Builder)super.setAnimatorProvider(animatorProvider);
        }

        public Builder setPrerenderEntry(Function<AzRendererPipelineContext<class_1799>, AzRendererPipelineContext<class_1799>> preRenderEntry) {
            return (Builder)super.setPrerenderEntry(preRenderEntry);
        }

        public Builder setRenderEntry(Function<AzRendererPipelineContext<class_1799>, AzRendererPipelineContext<class_1799>> renderEntry) {
            return (Builder)super.setRenderEntry(renderEntry);
        }

        public Builder setPostRenderEntry(Function<AzRendererPipelineContext<class_1799>, AzRendererPipelineContext<class_1799>> preRenderEntry) {
            return (Builder)super.setPostRenderEntry(preRenderEntry);
        }

        public Builder setAlpha(Function<class_1799, Float> alphaFunction) {
            return (Builder)super.setAlpha(alphaFunction);
        }

        public Builder setAlpha(float alpha) {
            return (Builder)super.setAlpha(alpha);
        }

        public Builder setScale(Function<class_1799, Float> scaleFunction) {
            return (Builder)super.setScale(scaleFunction);
        }

        public Builder setScale(Function<class_1799, Float> scaleHeightFunction, Function<class_1799, Float> scaleWidthFunction) {
            return (Builder)super.setScale(scaleHeightFunction, scaleWidthFunction);
        }

        public Builder setScale(float scale) {
            return (Builder)super.setScale(scale);
        }

        public Builder setScale(float scaleWidth, float scaleHeight) {
            return (Builder)super.setScale(scaleWidth, scaleHeight);
        }

        public Builder setBoneProvider(AzArmorBoneProvider boneProvider) {
            this.boneProvider = boneProvider;
            return this;
        }

        public AzArmorRendererConfig build() {
            AzRendererConfig baseConfig = super.build();
            return new AzArmorRendererConfig(baseConfig::createAnimator, this.boneProvider, baseConfig::modelLocation, baseConfig::getRenderType, baseConfig.renderLayers(), baseConfig::preRenderEntry, baseConfig::renderEntry, baseConfig::postRenderEntry, baseConfig::textureLocation, baseConfig::alpha, baseConfig::scaleHeight, baseConfig::scaleWidth);
        }
    }
}

