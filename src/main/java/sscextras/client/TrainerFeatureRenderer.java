package sscextras.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import sscextras.trainer.QuadrupedalTrainer;

public final class TrainerFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private final TrainerMoldModel mold = new TrainerMoldModel();
    private final CuffModel neck = new CuffModel(5,5);
    private final net.minecraft.client.model.ModelPart neckRails = new net.minecraft.client.model.ModelData().getRoot().addChild("supports",
            net.minecraft.client.model.ModelPartBuilder.create().uv(0,0)
                .cuboid(-2.8f,0,-.3f,.55f,2.25f,.6f).cuboid(2.25f,0,-.3f,.55f,2.25f,.6f)
                .cuboid(-.3f,0,2.25f,.6f,2.25f,.55f),net.minecraft.client.model.ModelTransform.NONE).createPart(32,32);
    public TrainerFeatureRenderer(PlayerEntityRenderer renderer) { super(renderer); }
    @Override public void render(MatrixStack m, VertexConsumerProvider b, int light, AbstractClientPlayerEntity p,
            float limbAngle, float limbDistance, float delta, float age, float yaw, float pitch) {
        if (p.isInvisible() || p.isSpectator()) return;
        var model = getContextModel();
        if (!QuadrupedalTrainer.equipped(p, QuadrupedalTrainer.NECK, true).isEmpty()) {
            m.push(); model.head.rotate(m); m.translate(0,.035,0); neck.render(m,b,light,false,0xb87ce8);
            neckRails.render(m,b.getBuffer(RenderLayer.getEntityCutoutNoCull(new net.minecraft.util.Identifier("ssc-extras","textures/entity/collar.png"))),light,OverlayTexture.DEFAULT_UV,.72f,.76f,.82f,1);
            m.translate(0,.14,0); neck.render(m,b,light,false,0xb87ce8); m.pop();
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
            limb(m,b,light,arm ? model.leftArm : model.leftLeg, arm, true, TrainerLimbModel.active(p,arm));
            limb(m,b,light,arm ? model.rightArm : model.rightLeg, arm, false, TrainerLimbModel.active(p,arm));
        }
    }
    private void limb(MatrixStack m, VertexConsumerProvider b, int light, ModelPart root, boolean arm, boolean left, boolean changed) {
        m.push(); root.rotate(m);
        if (changed) {
            for (String name : new String[]{"lower","pastern"}) {
                m.push(); TrainerLimbModel.joint(root,name).rotate(m); mold.brace(m,b,light); m.pop();
            }
            m.push(); TrainerLimbModel.joint(root,"paw").rotate(m); mold.foot(m,b,light); m.pop();
            var lower=TrainerLimbModel.joint(root,"lower");m.translate(lower.pivotX/16,lower.pivotY/16,lower.pivotZ/16);mold.band(m,b,light);
        } else { m.translate(arm ? (left?1:-1)/16f : 0, (arm?8.2:10.2)/16,0);mold.foot(m,b,light);mold.band(m,b,light); }
        m.pop();
    }
}
