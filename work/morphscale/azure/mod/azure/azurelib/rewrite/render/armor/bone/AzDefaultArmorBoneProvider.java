/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.Nullable
 */
package mod.azure.azurelib.rewrite.render.armor.bone;

import mod.azure.azurelib.rewrite.model.AzBakedModel;
import mod.azure.azurelib.rewrite.model.AzBone;
import mod.azure.azurelib.rewrite.render.armor.bone.AzArmorBoneProvider;
import org.jetbrains.annotations.Nullable;

public class AzDefaultArmorBoneProvider
implements AzArmorBoneProvider {
    @Override
    @Nullable
    public AzBone getHeadBone(AzBakedModel model) {
        return model.getBoneOrNull("armorHead");
    }

    @Override
    @Nullable
    public AzBone getBodyBone(AzBakedModel model) {
        return model.getBoneOrNull("armorBody");
    }

    @Override
    @Nullable
    public AzBone getRightArmBone(AzBakedModel model) {
        return model.getBoneOrNull("armorRightArm");
    }

    @Override
    @Nullable
    public AzBone getLeftArmBone(AzBakedModel model) {
        return model.getBoneOrNull("armorLeftArm");
    }

    @Override
    @Nullable
    public AzBone getRightLegBone(AzBakedModel model) {
        return model.getBoneOrNull("armorRightLeg");
    }

    @Override
    @Nullable
    public AzBone getLeftLegBone(AzBakedModel model) {
        return model.getBoneOrNull("armorLeftLeg");
    }

    @Override
    @Nullable
    public AzBone getRightBootBone(AzBakedModel model) {
        return model.getBoneOrNull("armorRightBoot");
    }

    @Override
    @Nullable
    public AzBone getLeftBootBone(AzBakedModel model) {
        return model.getBoneOrNull("armorLeftBoot");
    }
}

