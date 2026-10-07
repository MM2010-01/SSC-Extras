/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.jetbrains.annotations.Nullable
 */
package mod.azure.azurelib.rewrite.render.armor.bone;

import mod.azure.azurelib.rewrite.model.AzBakedModel;
import mod.azure.azurelib.rewrite.model.AzBone;
import org.jetbrains.annotations.Nullable;

public interface AzArmorBoneProvider {
    public static final String BONE_ARMOR_BODY_NAME = "armorBody";
    public static final String BONE_ARMOR_HEAD_NAME = "armorHead";
    public static final String BONE_ARMOR_LEFT_ARM_NAME = "armorLeftArm";
    public static final String BONE_ARMOR_RIGHT_ARM_NAME = "armorRightArm";
    public static final String BONE_ARMOR_LEFT_BOOT_NAME = "armorLeftBoot";
    public static final String BONE_ARMOR_RIGHT_BOOT_NAME = "armorRightBoot";
    public static final String BONE_ARMOR_LEFT_LEG_NAME = "armorLeftLeg";
    public static final String BONE_ARMOR_RIGHT_LEG_NAME = "armorRightLeg";

    @Nullable
    public AzBone getHeadBone(AzBakedModel var1);

    @Nullable
    public AzBone getBodyBone(AzBakedModel var1);

    @Nullable
    public AzBone getRightArmBone(AzBakedModel var1);

    @Nullable
    public AzBone getLeftArmBone(AzBakedModel var1);

    @Nullable
    public AzBone getRightLegBone(AzBakedModel var1);

    @Nullable
    public AzBone getLeftLegBone(AzBakedModel var1);

    @Nullable
    public AzBone getRightBootBone(AzBakedModel var1);

    @Nullable
    public AzBone getLeftBootBone(AzBakedModel var1);
}

