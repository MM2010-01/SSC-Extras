package sscextras.client;

import mod.azure.azurelib.rewrite.render.armor.bone.AzArmorBoneContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import static sscextras.client.ArmorRigTransform.*;

/** Native Morphscale ankle ornaments follow the newly articulated hock and paw. */
public final class TrainerArmorFit {
    private TrainerArmorFit() { }
    public static void fit(PlayerEntity player, PlayerEntityModel<?> model, AzArmorBoneContext bones, EquipmentSlot slot) {
        boolean quad=sscextras.player.PlayerStance.quadrupedal(player);
        if(model instanceof GuardianPlayerModel guardian && (quad||TrainerLimbModel.active(player,false))) {
            if(slot==EquipmentSlot.HEAD && quad && guardian.stage()<3) {
                // Horn tips are part of the authored head bounds, but the circlet
                // sits on the skull, not at the top of those tips.
                scale(bones.head,guardian.head.xScale,1,1);
            } else if(slot==EquipmentSlot.CHEST && quad) {
                // Native ornaments wrap the authored forearms; the vanilla shoulder
                // scale includes the Guardian's plates and is too wide here.
                for(boolean left:new boolean[]{true,false}) {
                    String owner=left?"bipedLeftArm":"bipedRightArm";
                    var bone=left?bones.leftArm:bones.rightArm;
                    var p=guardian.mesh().pivot(left?"left_forearm":"right_forearm");
                    p=TrainerGuardianRig.map(guardian,player,owner,p).add(left?-.08f:.08f,-.07f,.04f);
                    position(bone,guardian.posedPoint(owner,p),left?5:-5,2);scale(bone,1.15f,.65f,1.25f);
                }
            } else if(slot==EquipmentSlot.LEGS) {
                for(boolean left:new boolean[]{true,false}) {
                    String owner=left?"bipedLeftLeg":"bipedRightLeg";
                    var root=guardian.joint(owner);var bone=left?bones.leftLeg:bones.rightLeg;
                    var p=guardian.mesh().pivot(owner).add(left?-.025f:.025f,0,-.1f);
                    p=TrainerGuardianRig.map(guardian,player,owner,p);
                    var knee=TrainerGuardianRig.map(guardian,player,owner,TrainerGuardianRig.frameJoints(guardian,owner)[0]);
                    var hip=TrainerGuardianRig.map(guardian,player,owner,guardian.mesh().pivot(owner));
                    var pose=new net.minecraft.client.model.ModelPart(java.util.List.of(),java.util.Map.of());
                    pose.copyTransform(root);pose.pitch+=(float)Math.atan2(knee.z-hip.z,knee.y-hip.y);
                    if(bone!=null)mod.azure.azurelib.util.RenderUtils.matchModelPartRot(pose,bone);
                    position(bone,guardian.posedPoint(owner,p),left?2:-2,12);scale(bone,1.1f,.68f,1.15f);
                }
            }
        }
        if(slot!=EquipmentSlot.FEET || !TrainerLimbModel.active(player,false)
                && !(model instanceof GuardianPlayerModel && sscextras.player.PlayerStance.quadrupedal(player)))return;
        for(boolean left:new boolean[]{true,false}){
            var root=left?model.leftLeg:model.rightLeg;var bone=left?bones.leftBoot:bones.rightBoot;
            var p=TrainerLimbModel.point(root,"paw");
            if(model instanceof GuardianPlayerModel guardian){
                String owner=left?"bipedLeftLeg":"bipedRightLeg";
                p=TrainerGuardianRig.map(guardian,player,owner,guardian.mesh().pivot(left?"left_foot":"right_foot"));
                // The ornament's native ten-pixel ankle offset is local to the
                // leg. Rotate that offset with the same limb during each stride.
                p.y-=.62f;p=guardian.posedPoint(owner,p);
            } else p.y-=.62f;
            position(bone,p,left?2:-2,12);
            if(model instanceof GuardianPlayerModel guardian)scale(bone,1+guardian.stage()*.1f,1,1);
            else scale(bone,root.xScale*1.1f,1,root.zScale*1.1f);
        }
    }
}
