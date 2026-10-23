package sscextras.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import sscextras.player.PlayerStance;
import sscextras.trainer.QuadrupedalTrainer;

public final class TrainerFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final TrainerMoldModel mold = new TrainerMoldModel();
    private final TrainerNeckModel neck = new TrainerNeckModel();
    public TrainerFeatureRenderer(PlayerEntityRenderer renderer) { super(renderer); }
    @Override public void render(MatrixStack m, VertexConsumerProvider b, int light, AbstractClientPlayerEntity p,
            float limbAngle, float limbDistance, float delta, float age, float yaw, float pitch) {
        if (p.isInvisible() || p.isSpectator()) return;
        var model = getContextModel();
        if (!QuadrupedalTrainer.equipped(p, QuadrupedalTrainer.NECK, true).isEmpty()) {
            m.push();
            if(QuadrupedalTrainer.pitch(p)==-90)m.translate(0,-2.5/16,0);
            model.head.rotate(m);
            m.multiply(RotationAxis.POSITIVE_X.rotation(QuadrupedalTrainer.pitch(p)*MathHelper.RADIANS_PER_DEGREE-model.head.pitch));
            neck.render(m,b,light); m.pop();
        }
        for (boolean arm : new boolean[]{true,false}) {
            if (QuadrupedalTrainer.equipped(p,arm ? QuadrupedalTrainer.HANDS : QuadrupedalTrainer.FEET,true).isEmpty()) continue;
            if (model instanceof GuardianPlayerModel) continue; // The authored mesh fits its own deformed paw surfaces.
            if (model instanceof DrakeGolemPlayerModel golem) {
                for (String name : arm ? new String[]{"left_front_paw","right_front_paw"} : new String[]{"left_hock","right_hock"}) {
                    var bone=golem.mesh().bones.get(name);if(bone==null)continue;
                    m.push();golem.transform(m,name);TrainerMeshMolds.render(bone.parts(),m,b,light,point->point);m.pop();
                }
                continue;
            }
            // Native feral meshes have their own paw attachment layer.
            if (QuadrupedalTrainer.nativeDigitigrade(p)) continue;
            boolean ground=p.isOnGround()&&(PlayerStance.quadrupedal(p)||!arm)&&!p.isSwimming()&&!p.isFallFlying()&&!p.hasVehicle()&&!p.isSleeping();
            limb(m,b,light,arm ? model.leftArm : model.leftLeg, arm, true, TrainerLimbModel.active(p,arm),ground);
            limb(m,b,light,arm ? model.rightArm : model.rightLeg, arm, false, TrainerLimbModel.active(p,arm),ground);
        }
    }
    private void limb(MatrixStack m, VertexConsumerProvider b, int light, ModelPart root, boolean arm, boolean left, boolean changed, boolean ground) {
        m.push(); root.rotate(m);
        if (changed) {
            float sideReach=2.62f;
            for(String name:new String[]{"upper","lower","pastern"})
                sideReach=Math.max(sideReach,2.62f*TrainerLimbModel.joint(root,name).xScale);
            for (String name : new String[]{"upper","lower"}) {
                var joint=TrainerLimbModel.joint(root,name);
                float along=name.equals("upper")?1:2.6f;
                m.push(); joint.rotate(m);m.translate(0,along/16,0);
                // Keep a cuff's physical height fixed as its supporting bone
                // changes length; clear the skin's outer clothing layer too.
                m.scale(4.7f/4.35f,.8f/joint.yScale,4.7f/4.35f);mold.band(m,b,light);
                m.pop();
                for(int side:new int[]{-1,1}){
                    var atCuff=TrainerLimbModel.localPoint(joint,side*2.49f,along,0);
                    var atRail=new org.joml.Vector3f(joint.pivotX+side*sideReach,atCuff.y,atCuff.z);
                    mold.rod(m,b,light,atCuff,atRail);
                }
            }
            // One hinged rail down each side follows the actual joint centers.
            // Front/back corner routes crossed at tight knees and hocks; the
            // lateral route clears every joint without changing the anatomy.
            for(int side:new int[]{-1,1}){
                var upper=TrainerLimbModel.joint(root,"upper");
                var previous=TrainerLimbModel.localPoint(upper,0,1,0);previous.x+=side*sideReach;
                for(String name:new String[]{"lower","pastern","paw"}){
                    var joint=TrainerLimbModel.joint(root,name);
                    var next=new org.joml.Vector3f(joint.pivotX+side*sideReach,joint.pivotY,joint.pivotZ);
                    mold.rod(m,b,light,previous,next);previous=next;
                }
                var rim=TrainerLimbModel.localPoint(TrainerLimbModel.joint(root,"paw"),side*2.42f,0,0);
                mold.rod(m,b,light,previous,rim);
            }
            m.push(); TrainerLimbModel.joint(root,"paw").rotate(m); mold.foot(m,b,light); m.pop();
        } else {
            float x=arm ? (left?1:-1)/16f : 0;
            float upper=arm?1.5f:3.5f, lower=upper+4;
            float sole=TrainerLimbModel.moldAnkle(arm);
            for (float height : new float[]{upper,lower}) {
                m.push(); m.translate(x,height/16,0); mold.band(m,b,light);
                // Until anatomy changes, the rigid frame bridges the untouched
                // hand/foot to its ground support instead of stretching the body.
                m.scale(1,(height==upper?4.2f:Math.max(.5f,sole-.4f-lower))/4.2f,1);
                mold.brace(m,b,light); m.pop();
            }
            m.translate(x,sole/16,0);
            if(ground)m.multiply(RotationAxis.POSITIVE_X.rotation(TrainerLimbModel.supportPitch(root)));
            mold.foot(m,b,light);
        }
        m.pop();
    }
}
