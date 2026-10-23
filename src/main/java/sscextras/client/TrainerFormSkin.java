package sscextras.client;

import mod.azure.azurelib.cache.object.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.onixary.shapeShifterCurseFabric.render.form_render.*;

/** Apply a native form's own limb material to the existing articulated player joints. */
public final class TrainerFormSkin {
    private TrainerFormSkin() { }
    private record Material(FormRenderer renderer, GeoCube cube) { }
    private static Material material(AbstractClientPlayerEntity player, boolean slim, boolean arm, boolean left) {
        for (var renderer : FormRenderUtils.getPlayerAllFormRenderer(player)) {
            var model = renderer.realModel;
            boolean hidden = arm ? left ? model.Hidden_LeftArm : model.Hidden_RightArm : left ? model.Hidden_LeftLeg : model.Hidden_RightLeg;
            if (!hidden) continue;
            renderer.setPlayer(player,slim);
            var baked = model.getBakedModel(model.getModelResource((FormAnimatable)renderer.getAnimatable()));
            var bone = baked.getBone("biped" + (left ? "Left" : "Right") + (arm ? "Arm" : "Leg")).orElse(null);
            var cube = largest(bone);
            if (cube != null) return new Material(renderer,cube);
        }
        return null;
    }
    private static GeoCube largest(GeoBone bone) {
        if (bone == null) return null;
        GeoCube best = null;
        for (var cube : bone.getCubes()) if (best == null || volume(cube)>volume(best)) best=cube;
        for (var child : bone.getChildBones()) { var cube=largest(child); if(cube!=null&&(best==null||volume(cube)>volume(best)))best=cube; }
        return best;
    }
    private static double volume(GeoCube c) { return c.size().x*c.size().y*c.size().z; }
    public static boolean covers(AbstractClientPlayerEntity p,PlayerEntityModel<?> model,boolean arm,boolean left) {
        return material(p,p.getModel().equals("slim"),arm,left)!=null;
    }
    public static void render(AbstractClientPlayerEntity p,PlayerEntityModel<?> model,MatrixStack matrices,VertexConsumerProvider buffers,int light,ModelPart onlyArm) {
        if(p.isInvisible()||p.isSpectator())return;
        for(boolean arm:new boolean[]{true,false}) {
            if(!TrainerLimbModel.active(p,arm))continue;
            float padTint=TrainerLimbModel.padTint(sscextras.trainer.QuadrupedalTrainer.progress(p,arm?1:2));
            for(boolean left:new boolean[]{true,false}) {
                var root=arm?left?model.leftArm:model.rightArm:left?model.leftLeg:model.rightLeg;
                if(onlyArm!=null&&root!=onlyArm)continue;
                var material=material(p,p.getModel().equals("slim"),arm,left);if(material==null)continue;
                var form=material.renderer.realModel;
                var texture=form.getTextureResource(p.getModel().equals("slim"));
                var vertices=buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
                matrices.push();root.rotate(matrices);
                for(String name:new String[]{"upper","lower","pastern","knee_joint","hock_joint","paw","toe0","toe1","toe2"}) {
                    var joint=TrainerLimbModel.joint(root,name);if(!joint.visible)continue;
                    // Keep a continuous shoulder-to-paw strip instead of repeating
                    // the native limb's entire texture on each short segment.
                    float top=switch(name){case "upper"->0;case "lower"->1f/3;case "pastern"->2f/3;case "knee_joint"->.2f;case "hock_joint"->.55f;default->.84f;};
                    float bottom=switch(name){case "upper"->1f/3;case "lower"->2f/3;case "knee_joint"->.45f;case "hock_joint"->.78f;default->1;};
                    joint.forEachCuboid(matrices,(entry,path,index,cuboid)->emit(material.cube,cuboid,entry,vertices,light,path.contains("ssc_trainer_pad")?padTint:1,top,bottom));
                }
                matrices.pop();
            }
        }
    }
    private static void emit(GeoCube source,ModelPart.Cuboid target,MatrixStack.Entry entry,VertexConsumer vertices,int light,float tint,float top,float bottom) {
        var min=new org.joml.Vector3f(Float.POSITIVE_INFINITY);var max=new org.joml.Vector3f(Float.NEGATIVE_INFINITY);
        for(var face:source.quads())if(face!=null)for(var v:face.vertices()){min.min(v.position());max.max(v.position());}
        for(var face:source.quads())if(face!=null)for(var v:face.vertices()) {
            var q=v.position();var n=face.normal();
            float x=map(q.x,min.x,max.x,target.minX,target.maxX)/16;
            float y=map(q.y,min.y,max.y,target.maxY,target.minY)/16;
            // SSC rotates its Geo surface 180 degrees around X before attaching
            // it to the player. Both Y and Z must flip, including the normals.
            float z=map(q.z,min.z,max.z,target.maxZ,target.minZ)/16;
            float u=v.texU(),t=v.texV();
            for(var other:face.vertices()) {
                var r=other.position();
                if(Math.abs(q.x-r.x)>1e-6f||Math.abs(q.z-r.z)>1e-6f||Math.abs(q.y-r.y)<1e-6f)continue;
                var upper=q.y>r.y?v:other;var lower=q.y>r.y?other:v;
                float along=q.y>r.y?top:bottom;
                u=net.minecraft.util.math.MathHelper.lerp(along,upper.texU(),lower.texU());
                t=net.minecraft.util.math.MathHelper.lerp(along,upper.texV(),lower.texV());break;
            }
            vertices.vertex(entry.getPositionMatrix(),x,y,z).color(tint,tint,tint,1f).texture(u,t)
                    .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry.getNormalMatrix(),n.x,-n.y,-n.z).next();
        }
    }
    private static float map(float value,float low,float high,float toLow,float toHigh) {
        return net.minecraft.util.math.MathHelper.lerp(high-low<1e-6f?.5f:(value-low)/(high-low),toLow,toHigh);
    }
}
