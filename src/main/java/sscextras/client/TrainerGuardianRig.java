package sscextras.client;

import java.util.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import sscextras.player.PlayerStance;
import sscextras.trainer.QuadrupedalTrainer;

/** Deforms the Guardian's existing textured limbs through the trainer joints. */
public final class TrainerGuardianRig {
    private record Cache(float progress,float height,boolean ground,IdentityHashMap<RiggedMesh.Part,RiggedMesh.Part> parts) { }
    private static final Map<GuardianPlayerModel,Map<String,Cache>> CACHE=new WeakHashMap<>();
    private TrainerGuardianRig() { }
    public static void pose(GuardianPlayerModel model,PlayerEntity player,float angle,float distance,boolean statue) {
        if(!PlayerStance.quadrupedal(player)||player.isSleeping()||player.isSwimming()||player.isFallFlying()||player.hasVehicle())return;
        var body=model.body;body.pitch=MathHelper.HALF_PI;body.yaw=body.roll=0;
        body.pivotY=10;body.pivotZ=model.stage()<3?-6:0;
        if (QuadrupedalTrainer.nativeDigitigrade(player)) {
            // Pose the existing limbs without stretching or remodeling them. Solve
            // the torso angle/height from their actual sole heights.
            var shoulder=model.mesh().pivot("bipedLeftArm");
            var hip=model.mesh().pivot("bipedLeftLeg");
            float fore=model.mesh().limbBottom("bipedLeftArm")-shoulder.y;
            float hind=model.mesh().limbBottom("bipedLeftLeg")-hip.y;
            float dy=hip.y-shoulder.y,dz=hip.z-shoulder.z;
            body.pitch=(float)(Math.acos(MathHelper.clamp((fore-hind)/(float)Math.hypot(dy,dz),-1,1))-Math.atan2(dz,dy));
            var rest=model.mesh().pivot("bipedBody");
            body.pivotY=16*(1.5f-fore-MathHelper.cos(body.pitch)*(shoulder.y-rest.y)+MathHelper.sin(body.pitch)*(shoulder.z-rest.z));
        }
        float swing=(statue?0:1)*MathHelper.cos(angle*.6662f)*Math.min(.6f,distance)*.65f;
        for(String name:GuardianMesh.PLAYER_JOINTS){
            if(name.equals("bipedBody"))continue;
            var part=model.joint(name);var p=model.posedPoint("bipedBody",model.mesh().pivot(name));
            part.setPivot(p.x*16,p.y*16,p.z*16);
            if(name.endsWith("Arm")||name.endsWith("Leg")){
                part.pitch=(name.equals("bipedLeftArm")||name.equals("bipedRightLeg")?1:-1)*swing;part.yaw=part.roll=0;
            }
        }
    }
    public static boolean limb(PlayerEntity player,String owner) {
        if (QuadrupedalTrainer.nativeDigitigrade(player)) return false;
        return owner.endsWith("Arm")&&(TrainerLimbModel.active(player,true)||PlayerStance.quadrupedal(player))
                ||owner.endsWith("Leg")&&(TrainerLimbModel.active(player,false)||PlayerStance.quadrupedal(player));
    }
    public static RiggedMesh.Part deform(GuardianPlayerModel model,PlayerEntity player,String owner,RiggedMesh.Part source) {
        if(!limb(player,owner))return source;
        float progress=QuadrupedalTrainer.progress(player,owner.endsWith("Arm")?QuadrupedalTrainer.HANDS:QuadrupedalTrainer.FEET);
        boolean ground=PlayerStance.quadrupedal(player)&&!player.isSwimming()&&!player.isFallFlying()&&!player.isSleeping()&&!player.hasVehicle();
        var poses=CACHE.computeIfAbsent(model,key->new HashMap<>());var cache=poses.get(owner);
        if(cache==null||cache.progress!=progress||cache.height!=model.joint(owner).pivotY||cache.ground!=ground){
            cache=new Cache(progress,model.joint(owner).pivotY,ground,new IdentityHashMap<>());poses.put(owner,cache);
        }
        return cache.parts.computeIfAbsent(source,part->deformPart(model,player,owner,part));
    }
    private static RiggedMesh.Part deformPart(GuardianPlayerModel model,PlayerEntity player,String owner,RiggedMesh.Part source) {
        boolean arm=owner.endsWith("Arm");
        float amount=QuadrupedalTrainer.progress(player,arm?QuadrupedalTrainer.HANDS:QuadrupedalTrainer.FEET);
        var root=model.joint(owner);var rest=model.mesh().pivot(owner);
        float length=model.mesh().limbBottom(owner)-rest.y;
        boolean quad=PlayerStance.quadrupedal(player)&&!player.isSwimming()&&!player.isFallFlying()&&!player.isSleeping()&&!player.hasVehicle();
        float target=quad?1.5f-root.pivotY/16:length;
        var output=new ArrayList<Float>();float[] input=source.vertices();
        for(int i=0;i<input.length;i+=32){
            List<float[]> polygon=new ArrayList<>();
            for(int v=0;v<4;v++)polygon.add(Arrays.copyOfRange(input,i+v*8,i+v*8+8));
            float[] splits={Float.NEGATIVE_INFINITY,rest.y+length*.43f,rest.y+length*.73f,rest.y+length*.88f,Float.POSITIVE_INFINITY};
            for(int section=0;section<4;section++){
                var face=clip(clip(polygon,splits[section],true),splits[section+1],false);
                if(face.size()<3)continue;
                for(var v:face){
                    var mapped=map(model,player,owner,new Vector3f(v[0],v[1],v[2]));
                    v[0]=mapped.x;v[1]=mapped.y;v[2]=mapped.z;
                }
                for(int n=1;n<face.size()-1;n++){
                    var a=face.get(0);var b=face.get(n);var c=face.get(n+1);
                    var normal=new Vector3f(b[0]-a[0],b[1]-a[1],b[2]-a[2]).cross(c[0]-a[0],c[1]-a[1],c[2]-a[2]);
                    if(normal.lengthSquared()<1e-10f)continue;normal.normalize();
                    for(var v:new float[][]{a,b,c,c}){for(int k=0;k<5;k++)output.add(v[k]);output.add(normal.x);output.add(normal.y);output.add(normal.z);}
                }
            }
        }
        float[] values=new float[output.size()];for(int i=0;i<values.length;i++)values[i]=output.get(i);
        return new RiggedMesh.Part(source.texture(),source.core(),values,null);
    }
    public static Vector3f map(GuardianPlayerModel model,PlayerEntity player,String owner,Vector3f point) {
        if(!limb(player,owner))return point;
        boolean arm=owner.endsWith("Arm");float amount=QuadrupedalTrainer.progress(player,arm?QuadrupedalTrainer.HANDS:QuadrupedalTrainer.FEET);
        var rest=model.mesh().pivot(owner);float length=Math.max(.01f,model.mesh().limbBottom(owner)-rest.y);
        boolean quad=PlayerStance.quadrupedal(player)&&!player.isSwimming()&&!player.isFallFlying()&&!player.isSleeping()&&!player.hasVehicle();
        float target=quad?1.5f-model.joint(owner).pivotY/16:length;
        float t=(point.y-rest.y)/length;
        point.y=rest.y+(point.y-rest.y)*target/length;point.z+=bend(t,arm)*amount/16;
        if(t>.85f){float spread=(t-.85f)/.15f*amount;point.x=rest.x+(point.x-rest.x)*(1+.15f*spread);point.z-=.5f*spread/16;}
        return point;
    }
    private static float bend(float t,boolean arm){
        float[] at={0,.43f,.73f,.88f,1},z=arm?new float[]{0,1.3f,-.4f,-.4f,-.4f}:new float[]{0,-2.25f,1.9f,-.4f,-.4f};
        for(int i=0;i<4;i++)if(t<=at[i+1])return MathHelper.lerp(Math.max(0,(t-at[i])/(at[i+1]-at[i])),z[i],z[i+1]);
        return z[4];
    }
    private static List<float[]> clip(List<float[]> input,float plane,boolean above){
        if(!Float.isFinite(plane))return cloneFace(input);
        var result=new ArrayList<float[]>();if(input.isEmpty())return result;
        var prev=input.get(input.size()-1);boolean was=above?prev[1]>=plane:prev[1]<=plane;
        for(var curr:input){boolean inside=above?curr[1]>=plane:curr[1]<=plane;
            if(inside!=was){float t=(plane-prev[1])/(curr[1]-prev[1]);float[] v=new float[8];for(int k=0;k<8;k++)v[k]=MathHelper.lerp(t,prev[k],curr[k]);result.add(v);}
            if(inside)result.add(curr.clone());prev=curr;was=inside;
        }return result;
    }
    private static List<float[]> cloneFace(List<float[]> input){var out=new ArrayList<float[]>();for(var v:input)out.add(v.clone());return out;}
}
