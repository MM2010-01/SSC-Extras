package sscextras.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

/** Mechanical gait: planted support, eased motor strokes and brief endpoint holds. No render-time state. */
public final class DrakeGolemAnimation {
    private DrakeGolemAnimation() { }
    private static float smooth(float value) { float t=MathHelper.clamp(value,0,1); return t*t*(3-2*t); }
    private static float motor(float t) { return smooth((t-.12f)/.76f); }
    private static float wrap(float value) { return MathHelper.wrapDegrees(value*MathHelper.DEGREES_PER_RADIAN)*MathHelper.RADIANS_PER_DEGREE; }
    private static float servo(float degrees) {
        float step=degrees/5, base=(float)Math.floor(step);
        return (base+motor(step-base))*5*MathHelper.RADIANS_PER_DEGREE;
    }
    public static void pose(DrakeGolemPlayerModel model, AbstractClientPlayerEntity player, float angle, float distance,
            float age, float yaw, float pitch, float delta, float attack) {
        var rotations=new HashMap<String,Vector3f>();
        boolean frozen=sscextras.petrification.Petrification.frozen(player), sleeping=player.isInPose(net.minecraft.entity.EntityPose.SLEEPING);
        boolean crouch=player.isInSneakingPose(), airborne=!player.isOnGround() || player.isSwimming();
        float weight=frozen||sleeping?0:MathHelper.clamp(distance*4,0,1);
        float drop=sleeping?.25f:crouch?.125f:0;
        // Native limbAngle advances four units per travelled block. This cadence
        // matches the support stroke to that distance, avoiding long sliding holds.
        float cadence=crouch?2.5f:2f;
        float phase=angle*cadence/(2*(float)Math.PI);
        for (int side=0;side<2;side++) {
            String name=side==0?"left":"right", cap=side==0?"Left":"Right";
            for (boolean front:new boolean[]{true,false}) {
                float cycle=(phase+(side==0?0:.5f)+(front?0:.5f))%1;
                if (cycle<0) cycle++;
                float stride=(float)Math.PI*.66f/(4*cadence)*weight;
                float lift=0,z;
                if (cycle<.34f) {
                    float swing=cycle/.34f;
                    z=MathHelper.lerp(motor(swing),stride,-stride);
                    lift=(float)Math.sin(Math.PI*smooth(swing))*.14f*weight;
                } else z=MathHelper.lerp((cycle-.34f)/.66f,-stride,stride);
                if (!frozen && !sleeping && (airborne || player.hasVehicle())) lift+=player.hasVehicle()?.18f:.10f;
                if (front && side==1 && attack>0 && !frozen) {
                    float strike=(float)Math.sin(Math.PI*attack); lift+=strike*.10f; z-=strike*.12f;
                }
                solve(model,rotations,"biped"+cap+(front?"Arm":"Leg"),name+(front?"_forearm":"_hind_shin"),
                        name+(front?"_front_paw":"_hock"),-lift,z,drop,front?-1:1);
            }
        }
        rotations.put("neck",new Vector3f(sleeping?.10f:crouch?-.07f:0,0,0));
        rotations.put("bipedHead",new Vector3f(servo(MathHelper.clamp(pitch,-22,28))+(crouch?.07f:0),
                servo(MathHelper.clamp(yaw,-38,38)),0));
        float jaw=!frozen&&!sleeping&&(player.isUsingItem()||attack>0)?.13f*motor((age%12)/12):0;
        rotations.put("jaw",new Vector3f(jaw,0,0));
        for (int i=0;i<6;i++) {
            float turn=frozen||sleeping?0:(float)Math.sin(phase*2*Math.PI-i*.35f)*.025f*weight;
            rotations.put("tail_"+i,new Vector3f(0,turn,0));
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
