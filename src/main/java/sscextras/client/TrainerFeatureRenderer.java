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
            m.push(); model.head.rotate(m); neck.render(m,b,light); m.pop();
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
            boolean ground=PlayerStance.quadrupedal(p)&&!p.isSwimming()&&!p.isFallFlying()&&!p.hasVehicle()&&!p.isSleeping();
            limb(m,b,light,arm ? model.leftArm : model.leftLeg, arm, true, TrainerLimbModel.active(p,arm),ground);
            limb(m,b,light,arm ? model.rightArm : model.rightLeg, arm, false, TrainerLimbModel.active(p,arm),ground);
        }
    }
    private void limb(MatrixStack m, VertexConsumerProvider b, int light, ModelPart root, boolean arm, boolean left, boolean changed, boolean ground) {
        m.push(); root.rotate(m);
        if (changed) {
            for (String name : new String[]{"upper","lower"}) {
                m.push(); TrainerLimbModel.joint(root,name).rotate(m);
                m.translate(0,2/16f,0); mold.band(m,b,light);
                m.pop();
            }
            for (float x : new float[]{-2.05f,2.05f}) for (float z : new float[]{-2.22f,2.22f}) {
                org.joml.Vector3f previous = null;
                for (String name : new String[]{"upper","lower","pastern"}) {
                    var joint = TrainerLimbModel.joint(root,name);
                    var start = TrainerLimbModel.localPoint(joint,x,name.equals("upper")?2:0,z);
                    var end = TrainerLimbModel.localPoint(joint,x,4,z);
                    if (previous != null) mold.rod(m,b,light,previous,start);
                    mold.rod(m,b,light,start,end); previous=end;
                }
                var rim = TrainerLimbModel.localPoint(TrainerLimbModel.joint(root,"paw"),x,0,z);
                mold.rod(m,b,light,previous,rim);
            }
            m.push(); TrainerLimbModel.joint(root,"paw").rotate(m); mold.foot(m,b,light); m.pop();
        } else {
            float x=arm ? (left?1:-1)/16f : 0;
            float upper=arm?1.5f:3.5f, lower=upper+4;
            float sole=ground?(24-Math.max(0,root.pitch)*4-root.pivotY-2.5f)/Math.max(.5f,MathHelper.cos(root.pitch)):arm?8.2f:10.2f;
            for (float height : new float[]{upper,lower}) {
                m.push(); m.translate(x,height/16,0); mold.band(m,b,light);
                // Until anatomy changes, the rigid frame bridges the untouched
                // hand/foot to its ground support instead of stretching the body.
                m.scale(1,(height==upper?4.2f:Math.max(.5f,sole-.4f-lower))/4.2f,1);
                mold.brace(m,b,light); m.pop();
            }
            m.translate(x,sole/16,0);
            if(ground)m.multiply(RotationAxis.POSITIVE_X.rotation(-root.pitch));
            mold.foot(m,b,light);
        }
        m.pop();
    }
}
