package sscextras.client;

import mod.azure.azurelib.rewrite.model.AzBone;
import mod.azure.azurelib.rewrite.render.armor.bone.AzArmorBoneContext;
import net.minecraft.entity.EquipmentSlot;
import org.joml.Vector3f;
import static sscextras.client.ArmorRigTransform.*;

/** Morphscale ornaments attach to the current rig, including its separate ankle joints. */
public final class GuardianArmorFit {
    private GuardianArmorFit() { }

    public static void fit(GuardianPlayerModel model, AzArmorBoneContext bones, EquipmentSlot slot) {
        int stage = model.stage();
        var mesh = model.mesh();
        switch (slot) {
            case HEAD -> {
                scale(bones.head, model.head.xScale, stage == 3 ? .6f : model.head.yScale, 1);
                var head = mesh.pivot("bipedHead");
                if (stage == 3) head.z -= .035f;
                position(bones.head, model.posedPoint("bipedHead", head), 0, 0);
            }
            case CHEST -> {
                var chest = mesh.pivot("bipedBody");
                chest.z -= new float[]{0, .04f, .08f, .17f}[stage];
                if (stage == 3) chest.y -= .83f;
                position(bones.body, model.posedPoint("bipedBody", chest), 0, 0);
                scale(bones.body, model.body.xScale, stage == 3 ? .85f : model.body.yScale, stage >= 2 ? 1.2f : 1);
            }
            case FEET -> {
                ankle(model, bones.leftBoot, true);
                ankle(model, bones.rightBoot, false);
            }
            default -> { }
        }
    }

    private static void ankle(GuardianPlayerModel model, AzBone bone, boolean left) {
        var foot = model.mesh().pivot(left ? "left_foot" : "right_foot");
        foot.y -= .65f;
        position(bone, model.posedPoint(left ? "bipedLeftLeg" : "bipedRightLeg", foot), left ? 2 : -2, 12);
        scale(bone, 1 + model.stage() * .1f, 1, 1);
    }

}
