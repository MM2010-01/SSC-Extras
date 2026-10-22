package sscextras.client;

import mod.azure.azurelib.rewrite.model.AzBone;
import mod.azure.azurelib.rewrite.render.armor.bone.AzArmorBoneContext;
import mod.azure.azurelib.util.RenderUtils;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.EquipmentSlot;
import org.joml.Vector3f;
import static sscextras.client.ArmorRigTransform.*;

/** Native Morphscale ornaments fitted to the actual head, forearms, thighs and paws. */
public final class DrakeGolemArmorFit {
    private DrakeGolemArmorFit() { }
    public static void fit(DrakeGolemPlayerModel model, AzArmorBoneContext bones, EquipmentSlot slot) {
        switch (slot) {
            case HEAD -> {
                bind(model,bones.head,"bipedHead",model.rest("bipedHead").add(0,.11f,-.15f),0,0,0);
                scale(bones.head,.94f,.6f,1.12f);
            }
            case CHEST -> {
                bind(model,bones.body,"bipedBody",new Vector3f(0,.17f,-.52f),0,0,0);
                scale(bones.body,1.3f,.55f,.7f);
                arm(model,bones.leftArm,true); arm(model,bones.rightArm,false);
            }
            case LEGS -> {
                leg(model,bones.leftLeg,true); leg(model,bones.rightLeg,false);
            }
            case FEET -> {
                paw(model,bones.leftBoot,true); paw(model,bones.rightBoot,false);
            }
            default -> { }
        }
    }
    private static float axis(DrakeGolemPlayerModel model,String from,String to) {
        var direction=model.rest(to).sub(model.rest(from));return (float)Math.atan2(direction.z,direction.y);
    }
    private static void arm(DrakeGolemPlayerModel model,AzBone bone,boolean left) {
        String joint=left?"left_forearm":"right_forearm",end=left?"left_front_paw":"right_front_paw";
        bind(model,bone,joint,model.rest(joint).add(left?-.06f:.06f,.04f,.03f),left?5:-5,2,axis(model,joint,end));scale(bone,1.05f,.7f,1.35f);
    }
    private static void leg(DrakeGolemPlayerModel model,AzBone bone,boolean left) {
        String joint=left?"bipedLeftLeg":"bipedRightLeg",end=left?"left_hind_shin":"right_hind_shin";
        bind(model,bone,joint,model.rest(joint).add(left?-.04f:.04f,.05f,-.08f),left?2:-2,12,axis(model,joint,end));scale(bone,1.1f,.68f,1.15f);
    }
    private static void paw(DrakeGolemPlayerModel model,AzBone bone,boolean left) {
        String joint=left?"left_hind_paw":"right_hind_paw";
        var point=model.rest(joint).add(left?-.068f:.068f,-.63f,.05f);
        bind(model,bone,joint,point,left?2:-2,12,0);scale(bone,1.15f,1,1.3f);
    }
    private static void bind(DrakeGolemPlayerModel model,AzBone bone,String joint,Vector3f point,float x,float y,float restPitch) {
        if (bone==null) return;
        var part=new ModelPart(java.util.List.of(),java.util.Map.of());part.copyTransform(model.joint(joint));
        part.pitch+=restPitch; RenderUtils.matchModelPartRot(part,bone);
        position(bone,model.posedPoint(joint,point),x,y);
    }
}
