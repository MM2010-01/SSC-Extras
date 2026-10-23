package sscextras.client;

import mod.azure.azurelib.cache.object.GeoBone;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import sscextras.petrification.Petrification;

/** Save and restore the existing Azure/SSC rig by resource and bone name. */
public final class PetrificationFormPose {
    private PetrificationFormPose() { }
    public static void apply(PlayerEntity player, FormModel model, net.minecraft.client.render.entity.PlayerEntityRenderer renderer) {
        var offsets=new java.util.HashMap<String,net.minecraft.util.math.Vec3d>();
        if (model.AnimationSystem instanceof net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem system) {
            var rig=renderer.getModel();
            offsets.put(system.RM_HeadGeoBoneID,PetrificationModelPose.playerRenderOffset(rig.head));
            offsets.put(system.RM_BodyGeoBoneID,PetrificationModelPose.playerRenderOffset(rig.body));
            offsets.put(system.RM_LeftArmGeoBoneID,PetrificationModelPose.playerRenderOffset(rig.leftArm));
            offsets.put(system.RM_RightArmGeoBoneID,PetrificationModelPose.playerRenderOffset(rig.rightArm));
            offsets.put(system.RM_LeftLegGeoBoneID,PetrificationModelPose.playerRenderOffset(rig.leftLeg));
            offsets.put(system.RM_RightLegGeoBoneID,PetrificationModelPose.playerRenderOffset(rig.rightLeg));
        }
        boolean slim=player instanceof net.minecraft.client.network.AbstractClientPlayerEntity client && client.getModel().equals("slim");
        apply(player, model.getModelResource(slim).toString(), model.getAnimationProcessor().getRegisteredBones(), offsets);
    }
    public static void apply(LivingEntity entity, String resource, Iterable<?> bones) {
        apply(entity,resource,bones,java.util.Map.of());
    }
    private static void apply(LivingEntity entity, String resource, Iterable<?> bones, java.util.Map<String,net.minecraft.util.math.Vec3d> offsets) {
        if (!Petrification.poseLocked(entity)) return;
        for (var item : bones) {
            var bone = (GeoBone)item;
            var offset=offsets.getOrDefault(bone.getName(),net.minecraft.util.math.Vec3d.ZERO);
            var v = Petrification.state(entity).pose().retain("bone/" + resource + "/" + bone.getName(), new float[]{
                    bone.getPosX()-(float)offset.x,bone.getPosY()-(float)offset.y,bone.getPosZ()-(float)offset.z,
                    bone.getRotX(),bone.getRotY(),bone.getRotZ(),bone.getScaleX(),bone.getScaleY(),bone.getScaleZ()});
            bone.setPosX(v[0]+(float)offset.x); bone.setPosY(v[1]+(float)offset.y); bone.setPosZ(v[2]+(float)offset.z);
            bone.setRotX(v[3]); bone.setRotY(v[4]); bone.setRotZ(v[5]);
            bone.setScaleX(v[6]); bone.setScaleY(v[7]); bone.setScaleZ(v[8]);
        }
    }
}
