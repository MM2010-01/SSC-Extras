/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_1297
 *  net.minecraft.class_1304
 *  net.minecraft.class_1799
 *  net.minecraft.class_572
 *  org.jetbrains.annotations.Nullable
 */
package mod.azure.azurelib.rewrite.render.armor;

import mod.azure.azurelib.rewrite.animation.impl.AzItemAnimator;
import mod.azure.azurelib.rewrite.model.AzBakedModel;
import mod.azure.azurelib.rewrite.render.AzProvider;
import mod.azure.azurelib.rewrite.render.AzRendererConfig;
import mod.azure.azurelib.rewrite.render.armor.AzArmorRendererPipeline;
import net.minecraft.class_1297;
import net.minecraft.class_1304;
import net.minecraft.class_1799;
import net.minecraft.class_572;
import org.jetbrains.annotations.Nullable;

public class AzArmorRenderer {
    private final AzProvider<class_1799> provider = new AzProvider<class_1799>(config::createAnimator, config::modelLocation);
    private final AzArmorRendererPipeline rendererPipeline;
    @Nullable
    private AzItemAnimator reusedAzItemAnimator;

    public AzArmorRenderer(AzRendererConfig<class_1799> config) {
        this.rendererPipeline = this.createPipeline(config);
    }

    protected AzArmorRendererPipeline createPipeline(AzRendererConfig config) {
        return new AzArmorRendererPipeline(config, this);
    }

    public void prepForRender(@Nullable class_1297 entity, class_1799 stack, @Nullable class_1304 slot, @Nullable class_572<?> baseModel) {
        if (entity == null || slot == null || baseModel == null) {
            return;
        }
        this.rendererPipeline.context().prepare(entity, stack, slot, baseModel);
        AzBakedModel model = this.provider.provideBakedModel(stack);
        this.prepareAnimator(stack, model);
    }

    private void prepareAnimator(class_1799 stack, AzBakedModel model) {
        AzItemAnimator cachedEntityAnimator = (AzItemAnimator)this.provider.provideAnimator(stack);
        if (cachedEntityAnimator != null && model != null) {
            cachedEntityAnimator.setActiveModel(model);
        }
        this.reusedAzItemAnimator = cachedEntityAnimator;
    }

    @Nullable
    public AzItemAnimator animator() {
        return this.reusedAzItemAnimator;
    }

    public AzProvider<class_1799> provider() {
        return this.provider;
    }

    public AzArmorRendererPipeline rendererPipeline() {
        return this.rendererPipeline;
    }
}

