package sscextras.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Drake-like diagonal strides retargeted to the stone rig, without stepped motor holds. */
public final class DrakeGolemAnimation {
    private DrakeGolemAnimation() { }
    private static float wrap(float value) { return MathHelper.wrapDegrees(value*MathHelper.DEGREES_PER_RADIAN)*MathHelper.RADIANS_PER_DEGREE; }
    public static void pose(DrakeGolemPlayerModel model, AbstractClientPlayerEntity player, float angle, float distance,
            float age, float yaw, float pitch, float delta, float attack) {
        var rotations=new HashMap<String,Vector3f>();
        boolean frozen=sscextras.petrification.Petrification.poseLocked(player), sleeping=player.isInPose(net.minecraft.entity.EntityPose.SLEEPING);
        boolean crouch=player.isInSneakingPose(), airborne=!player.isOnGround() || player.isSwimming();
        float weight=frozen||sleeping?0:MathHelper.clamp(distance*1.5f,0,1);
        float drop=sleeping?.25f:crouch?.125f:0;
        // Use native interpolated limb motion at SSC's Drake tail/stride cadence.
        // The Drake clips contain anatomy-specific translations and a 90-degree
        // torso, so the saved stone limbs retain their own ground-contact solve.
        float phase=angle*.6662f;
        for (int side=0;side<2;side++) {
            String name=side==0?"left":"right", cap=side==0?"Left":"Right";
            for (boolean front:new boolean[]{true,false}) {
                float cycle=phase+(side==0?0:(float)Math.PI)+(front?0:(float)Math.PI);
                // A small third harmonic rounds the motor reversal without stops
                // or discontinuities. Lift eases to zero before the support stroke.
                float stroke=.94f*MathHelper.cos(cycle)+.06f*MathHelper.cos(3*cycle);
                float swing=Math.max(0,MathHelper.sin(cycle));
                float z=stroke*(crouch?.19f:.25f)*weight;
                float lift=swing*swing*(crouch?.11f:.16f)*weight;
                if (!frozen && !sleeping && (airborne || player.hasVehicle())) lift+=player.hasVehicle()?.18f:.10f;
                if (front && side==1 && attack>0 && !frozen) {
                    float strike=(float)Math.sin(Math.PI*attack); lift+=strike*.10f; z-=strike*.12f;
                }
                solve(model,rotations,"biped"+cap+(front?"Arm":"Leg"),name+(front?"_forearm":"_hind_shin"),
                        name+(front?"_front_paw":"_hock"),-lift,z,drop,front?-1:1);
            }
        }
        float lookYaw=MathHelper.clamp(yaw,-65,65)*MathHelper.RADIANS_PER_DEGREE;
        float lookPitch=MathHelper.clamp(pitch,-40,45)*MathHelper.RADIANS_PER_DEGREE;
        var neck=new Vector3f(sleeping?.10f:lookPitch*.30f+(crouch?-.07f:0),sleeping?0:lookYaw*.30f,0);
        rotations.put("neck",neck);
        // Share the camera direction between neck and head. Composition keeps the
        // final head aim exact even when yaw and pitch are applied together.
        rotations.put("bipedHead",new Matrix3f().rotation(new Quaternionf().rotationZYX(neck.z,neck.y,neck.x).invert()
                .mul(new Quaternionf().rotationYXZ(sleeping?0:lookYaw,sleeping?0:lookPitch,0)))
                .getEulerAnglesZYX(new Vector3f()));
        float jaw=!frozen&&!sleeping&&(player.isUsingItem()||attack>0)?.065f*(1-MathHelper.cos(age*(float)Math.PI/6)):0;
        rotations.put("jaw",new Vector3f(jaw,0,0));
        var drag=model.tailDrag();
        for (int i=0;i<6;i++) {
            float idle=MathHelper.sin(age/6f-i*.75f)*.035f;
            float balance=MathHelper.sin(phase-i*.45f)*weight*(i==0?.075f:.025f);
            float turn=idle+balance+drag.horizontal()*(i==0?.22f:.12f);
            float rise=-drag.vertical()*(i==0?.14f:.055f);
            rotations.put("tail_"+i,frozen||sleeping?new Vector3f():new Vector3f(rise,turn,0));
        }
        model.applyPose(rotations,drop);
    }
    private static void solve(DrakeGolemPlayerModel model, Map<String,Vector3f> rotations, String upper, String lower,
            String end, float lift, float stride, float drop, int branch) {
        var a=model.rest(upper); var b=model.rest(lower); var c=model.rest(end);
        var v1=new Vector3f(b).sub(a); var v2=new Vector3f(c).sub(b);
        float y=c.y+lift-a.y-drop,z=c.z+stride-a.z;
        double l1=Math.hypot(v1.y,v1.z),l2=Math.hypot(v2.y,v2.z);
        double beta=branch*Math.acos(MathHelper.clamp((y*y+z*z-l1*l1-l2*l2)/(2*l1*l2),-1,1));
        double theta=Math.atan2(z,y)-Math.atan2(l2*Math.sin(beta),l1+l2*Math.cos(beta));
        float first=wrap((float)(theta-Math.atan2(v1.z,v1.y)));
        float rest=wrap((float)(Math.atan2(v2.z,v2.y)-Math.atan2(v1.z,v1.y)));
        float second=wrap((float)beta-rest);
        rotations.put(upper,new Vector3f(first,0,0)); rotations.put(lower,new Vector3f(second,0,0));
        rotations.put(end,new Vector3f(-first-second,0,0));
    }
}
