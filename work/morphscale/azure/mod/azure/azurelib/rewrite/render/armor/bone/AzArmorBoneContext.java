/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.class_1304
 *  net.minecraft.class_572
 *  net.minecraft.class_630
 *  org.jetbrains.annotations.Nullable
 */
package mod.azure.azurelib.rewrite.render.armor.bone;

import mod.azure.azurelib.rewrite.model.AzBakedModel;
import mod.azure.azurelib.rewrite.model.AzBone;
import mod.azure.azurelib.rewrite.render.armor.bone.AzArmorBoneProvider;
import mod.azure.azurelib.util.RenderUtils;
import net.minecraft.class_1304;
import net.minecraft.class_572;
import net.minecraft.class_630;
import org.jetbrains.annotations.Nullable;

public class AzArmorBoneContext {
    private AzBakedModel lastModel;
    public AzBone head = null;
    public AzBone body = null;
    public AzBone rightArm = null;
    public AzBone leftArm = null;
    public AzBone rightLeg = null;
    public AzBone leftLeg = null;
    public AzBone rightBoot = null;
    public AzBone leftBoot = null;

    public void setAllVisible(boolean pVisible) {
        this.setBoneVisible(this.head, pVisible);
        this.setBoneVisible(this.body, pVisible);
        this.setBoneVisible(this.rightArm, pVisible);
        this.setBoneVisible(this.leftArm, pVisible);
        this.setBoneVisible(this.rightLeg, pVisible);
        this.setBoneVisible(this.leftLeg, pVisible);
        this.setBoneVisible(this.rightBoot, pVisible);
        this.setBoneVisible(this.leftBoot, pVisible);
    }

    public void grabRelevantBones(AzBakedModel model, AzArmorBoneProvider boneProvider) {
        if (this.lastModel == model) {
            return;
        }
        this.lastModel = model;
        this.head = boneProvider.getHeadBone(model);
        this.body = boneProvider.getBodyBone(model);
        this.rightArm = boneProvider.getRightArmBone(model);
        this.leftArm = boneProvider.getLeftArmBone(model);
        this.rightLeg = boneProvider.getRightLegBone(model);
        this.leftLeg = boneProvider.getLeftLegBone(model);
        this.rightBoot = boneProvider.getRightBootBone(model);
        this.leftBoot = boneProvider.getLeftBootBone(model);
    }

    public void applyBaseTransformations(class_572<?> baseModel) {
        if (this.head != null) {
            class_630 headPart = baseModel.field_3398;
            RenderUtils.matchModelPartRot(headPart, this.head);
            this.head.updatePosition(headPart.field_3657, -headPart.field_3656, headPart.field_3655);
        }
        if (this.body != null) {
            class_630 bodyPart = baseModel.field_3391;
            RenderUtils.matchModelPartRot(bodyPart, this.body);
            this.body.updatePosition(bodyPart.field_3657, -bodyPart.field_3656, bodyPart.field_3655);
        }
        if (this.rightArm != null) {
            class_630 rightArmPart = baseModel.field_3401;
            RenderUtils.matchModelPartRot(rightArmPart, this.rightArm);
            this.rightArm.updatePosition(rightArmPart.field_3657 + 5.0f, 2.0f - rightArmPart.field_3656, rightArmPart.field_3655);
        }
        if (this.leftArm != null) {
            class_630 leftArmPart = baseModel.field_27433;
            RenderUtils.matchModelPartRot(leftArmPart, this.leftArm);
            this.leftArm.updatePosition(leftArmPart.field_3657 - 5.0f, 2.0f - leftArmPart.field_3656, leftArmPart.field_3655);
        }
        if (this.rightLeg != null) {
            class_630 rightLegPart = baseModel.field_3392;
            RenderUtils.matchModelPartRot(rightLegPart, this.rightLeg);
            this.rightLeg.updatePosition(rightLegPart.field_3657 + 2.0f, 12.0f - rightLegPart.field_3656, rightLegPart.field_3655);
            if (this.rightBoot != null) {
                RenderUtils.matchModelPartRot(rightLegPart, this.rightBoot);
                this.rightBoot.updatePosition(rightLegPart.field_3657 + 2.0f, 12.0f - rightLegPart.field_3656, rightLegPart.field_3655);
            }
        }
        if (this.leftLeg != null) {
            class_630 leftLegPart = baseModel.field_3397;
            RenderUtils.matchModelPartRot(leftLegPart, this.leftLeg);
            this.leftLeg.updatePosition(leftLegPart.field_3657 - 2.0f, 12.0f - leftLegPart.field_3656, leftLegPart.field_3655);
            if (this.leftBoot != null) {
                RenderUtils.matchModelPartRot(leftLegPart, this.leftBoot);
                this.leftBoot.updatePosition(leftLegPart.field_3657 - 2.0f, 12.0f - leftLegPart.field_3656, leftLegPart.field_3655);
            }
        }
    }

    public void applyBoneVisibilityByPart(class_1304 currentSlot, class_630 currentPart, class_572<?> model) {
        this.setAllVisible(false);
        currentPart.field_3665 = true;
        AzBone bone = null;
        if (currentPart == model.field_3394 || currentPart == model.field_3398) {
            bone = this.head;
        } else if (currentPart == model.field_3391) {
            bone = this.body;
        } else if (currentPart == model.field_27433) {
            bone = this.leftArm;
        } else if (currentPart == model.field_3401) {
            bone = this.rightArm;
        } else if (currentPart == model.field_3397) {
            bone = currentSlot == class_1304.field_6166 ? this.leftBoot : this.leftLeg;
        } else if (currentPart == model.field_3392) {
            AzBone azBone = bone = currentSlot == class_1304.field_6166 ? this.rightBoot : this.rightLeg;
        }
        if (bone != null) {
            bone.setHidden(false);
        }
    }

    public void applyBoneVisibilityBySlot(class_1304 currentSlot) {
        this.setAllVisible(false);
        switch (currentSlot) {
            case field_6169: {
                this.setBoneVisible(this.head, true);
                break;
            }
            case field_6174: {
                this.setBoneVisible(this.body, true);
                this.setBoneVisible(this.rightArm, true);
                this.setBoneVisible(this.leftArm, true);
                break;
            }
            case field_6172: {
                this.setBoneVisible(this.rightLeg, true);
                this.setBoneVisible(this.leftLeg, true);
                break;
            }
            case field_6166: {
                this.setBoneVisible(this.rightBoot, true);
                this.setBoneVisible(this.leftBoot, true);
                break;
            }
        }
    }

    protected void setBoneVisible(@Nullable AzBone bone, boolean visible) {
        if (bone == null) {
            return;
        }
        bone.setHidden(!visible);
    }
}

