package sscextras.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import sscextras.player.PlayerStance;
import sscextras.trainer.QuadrupedalTrainer;

/** Articulated children of the actual native limb roots, using the wearer's skin UVs. */
public final class TrainerLimbModel {
    public interface Part { void sscExtras$reshaped(boolean active); boolean sscExtras$reshaped(); void sscExtras$padTint(float tint); float sscExtras$padTint(); }
    private static final String PREFIX = "ssc_trainer_";
    private TrainerLimbModel() { }

    public static void add(ModelData data, boolean slim) {
        for (String name : new String[]{"left_arm", "right_arm", "left_leg", "right_leg", "left_sleeve", "right_sleeve", "left_pants", "right_pants"}) {
            boolean arm = name.contains("arm") || name.contains("sleeve"), left = name.startsWith("left");
            boolean outer = name.contains("sleeve") || name.contains("pants");
            int u = arm ? left ? 32 : 40 : left ? 16 : 0;
            int v = left ? 48 : outer ? 32 : 16;
            if (outer && left) u = arm ? 48 : 0;
            var limb = data.getRoot().getChild(name);
            float width = arm && slim ? 3 : 4;
            var dilation = new Dilation(outer ? .25f : 0);
            int strip = 0;
            for (String segment : new String[]{"upper", "lower", "pastern"}) {
                limb.addChild(PREFIX + segment, ModelPartBuilder.create().uv(u, v + strip)
                        .cuboid(-width / 2, -.1f, -2, width, 4.2f, 4, dilation), ModelTransform.NONE);
                strip += 4;
            }
            limb.addChild(PREFIX + "knee_joint", ModelPartBuilder.create().uv(u, v + 3)
                    .cuboid(-2, -1.65f, -2, 4, 3.3f, 4, dilation), ModelTransform.NONE);
            limb.addChild(PREFIX + "hock_joint", ModelPartBuilder.create().uv(u, v + 7)
                    .cuboid(-1.65f, -1.45f, -1.65f, 3.3f, 2.9f, 3.3f, dilation), ModelTransform.NONE);
            var paw=limb.addChild(PREFIX + "paw", ModelPartBuilder.create().uv(u, v + 5)
                    .cuboid(-2, 0, -2, 4, 1.8f, 4, dilation), ModelTransform.NONE);
            if(!outer)paw.addChild(PREFIX+"pad",ModelPartBuilder.create().uv(u+4,v+9)
                    .cuboid(-1.25f,0,-1.35f,2.5f,.18f,2.5f),ModelTransform.pivot(0,1.8f,0));
            for (int toe = 0; toe < 3; toe++) {
                var tip=limb.addChild(PREFIX + "toe" + toe,
                    ModelPartBuilder.create().uv(u + 4, v + 8).cuboid(-.625f, 0, -2.5f, 1.25f, 1.7f, 2.5f, dilation), ModelTransform.NONE);
                if(!outer)tip.addChild(PREFIX+"pad",ModelPartBuilder.create().uv(u+4,v+9)
                        .cuboid(-.42f,0,-.7f,.84f,.16f,1.4f),ModelTransform.pivot(0,1.7f,-1.5f));
            }
        }
    }

    public static boolean active(PlayerEntity player, boolean arms) {
        return QuadrupedalTrainer.reshapes(player, arms ? QuadrupedalTrainer.HANDS : QuadrupedalTrainer.FEET);
    }

    public static void apply(PlayerEntityModel<?> model, PlayerEntity player) {
        boolean quad = PlayerStance.quadrupedal(player);
        boolean canPlant = player.isOnGround() && !player.isSwimming() && !player.isFallFlying() && !player.hasVehicle() && !player.isSleeping();
        boolean hands = active(player, true), feet = active(player, false);
        // Native shoulders/hips keep their owner and animation; the new joints
        // grow beneath them. Cloth and skin receive the exact same articulation.
        for (int i = 0; i < 4; i++) {
            boolean arm = i < 2, left = i % 2 == 0;
            boolean ground=canPlant&&(quad||!arm);
            var root = arm ? left ? model.leftArm : model.rightArm : left ? model.leftLeg : model.rightLeg;
            var cloth = arm ? left ? model.leftSleeve : model.rightSleeve : left ? model.leftPants : model.rightPants;
            boolean enabled = arm ? hands : feet;
            float progress = QuadrupedalTrainer.progress(player, arm ? QuadrupedalTrainer.HANDS : QuadrupedalTrainer.FEET);
            boolean worn=!QuadrupedalTrainer.equipped(player,arm?QuadrupedalTrainer.HANDS:QuadrupedalTrainer.FEET,false).isEmpty();
            if (!arm && (enabled || worn) && canPlant && !quad && !(model instanceof AuthoredPlayerModel)) {
                // A short, smooth stride keeps the fixed bones' support arc low.
                // The former full vanilla swing needed a large whole-body bob.
                root.pitch=.38f*(float)Math.tanh(root.pitch/.65f);
                cloth.copyTransform(root);
            }
            if(!arm&&(enabled||worn)&&!QuadrupedalTrainer.nativeDigitigrade(player)) {
                // The hips spread with the growing hind paws; the rigid mold needs
                // its own initial clearance before the anatomy fills it.
                float half=worn?2.9f+.65f*progress:2+.75f*progress;
                root.pivotX=(left?1:-1)*Math.max(Math.abs(root.pivotX),half);
                cloth.pivotX=root.pivotX;
            }
            float end = arm ? 10 : 12;
            // Articulated bones retain their length during a stride. Projecting
            // the paw onto the floor divided by cos(pitch) stretched the shin.
            if (quad || !arm) end = 12 - (worn ? .7f : .18f*progress);
            reshape(root, arm, left, enabled, progress, end, ground,false);
            reshape(cloth, arm, left, enabled, progress, end, ground,true);
            if (enabled && ground) {
                // Only the near-vertical support phase levels the paw. As the
                // limb swings clear it rolls with the leg; airborne paws are free.
                float pawPitch = supportPitch(root);
                root.getChild(PREFIX + "paw").pitch = pawPitch;
                cloth.getChild(PREFIX + "paw").pitch = pawPitch;
                toes(root, progress, false);
                toes(cloth, progress, true);
            }
        }
        if (canPlant && (feet || (QuadrupedalTrainer.equipment(player)&QuadrupedalTrainer.FEET)!=0 || quad&&hands)
                && !(model instanceof AuthoredPlayerModel) && !QuadrupedalTrainer.nativeDigitigrade(player)) {
            float contact=Float.NEGATIVE_INFINITY;
            for(int i=quad?0:2;i<4;i++) {
                boolean arm=i<2;
                var root=arm?i==0?model.leftArm:model.rightArm:i==2?model.leftLeg:model.rightLeg;
                boolean changed=arm?hands:feet;
                boolean worn=!QuadrupedalTrainer.equipped(player,arm?QuadrupedalTrainer.HANDS:QuadrupedalTrainer.FEET,false).isEmpty();
                if(changed)contact=Math.max(contact,soleHeight(root,worn,QuadrupedalTrainer.progress(player,arm?QuadrupedalTrainer.HANDS:QuadrupedalTrainer.FEET)));
                else if(worn)contact=Math.max(contact,root.pivotY+MathHelper.cos(root.pitch)*moldAnkle(arm,quad)+2.5f);
            }
            // SSC's global animation translation precedes the player's 15/16
            // render scale. Include it instead of treating model Y=24 as ground.
            float settle=floorHeight(player)-contact;
            // Standing and walking need contact in both directions. Only the
            // native sprint bound deliberately lifts every supporting paw.
            if(quad && player.isSprinting())settle=Math.min(0,settle);
            for(var part:new ModelPart[]{model.head,model.hat,model.body,model.jacket,
                    model.leftArm,model.rightArm,model.leftSleeve,model.rightSleeve,
                    model.leftLeg,model.rightLeg,model.leftPants,model.rightPants}) part.pivotY+=settle;
        }
        if (QuadrupedalTrainer.neckLocked(player)) {
            // The supported head follows real aim throughout the permitted range,
            // including past the generic all-fours pose's narrower clamp.
            model.head.pitch = QuadrupedalTrainer.clampPitch(player, player.getPitch()) * MathHelper.RADIANS_PER_DEGREE;
            model.hat.copyTransform(model.head);
        }
    }

    public static float supportPitch(ModelPart root) {
        float weight=MathHelper.clamp(1-Math.abs(root.pitch)/.55f,0,1);
        return -root.pitch*weight*weight*(3-2*weight);
    }
    // The untouched foot clothing ends at Y=12.25. Clear it with the bed's
    // inner floor, extending the fore-frame equally when all four molds support
    // the body. Upright hands retain their shorter, non-supporting reach.
    public static float moldAnkle(boolean arm,boolean quadrupedal) { return arm?(quadrupedal?10.5f:9.3f):10.7f; }
    public static float floorHeight(PlayerEntity player) {
        float floor=24;
        if(player instanceof dev.kosmx.playerAnim.impl.IAnimatedPlayer animated && animated.playerAnimator_getAnimation().isActive())
            floor+=animated.playerAnimator_getAnimation().get3DTransform("body",dev.kosmx.playerAnim.api.TransformType.POSITION,dev.kosmx.playerAnim.core.util.Vec3f.ZERO).getY()*16/.9375f;
        return floor;
    }
    public static float soleHeight(ModelPart root,boolean worn,float progress) {
        var paw=root.getChild(PREFIX+"paw");
        float lowest=Float.NEGATIVE_INFINITY;
        for(float x:new float[]{worn?-1.4f:-1.25f,worn?1.4f:1.25f})for(float z:new float[]{worn?-3.9f:-1.35f,worn?1.4f:1.15f}) {
            var point=worn?new Vector3f(x,2.5f,z):localPoint(paw.getChild(PREFIX+"pad"),x,.18f,z);
            point=localPoint(paw,point.x,point.y,point.z);
            lowest=Math.max(lowest,localPoint(root,point.x,point.y,point.z).y);
        }
        if(!worn)for(int i=0;i<3;i++)for(float z:new float[]{-.7f,.7f}) {
            var toe=root.getChild(PREFIX+"toe"+i);
            var point=localPoint(toe.getChild(PREFIX+"pad"),0,.16f,z);
            point=localPoint(toe,point.x,point.y,point.z);
            lowest=Math.max(lowest,localPoint(root,point.x,point.y,point.z).y);
        }
        return lowest;
    }

    private static void reshape(ModelPart root, boolean arm, boolean left, boolean enabled, float amount, float end, boolean quad,boolean outer) {
        if (!root.hasChild(PREFIX + "upper")) return;
        ((Part)(Object)root).sscExtras$reshaped(enabled);
        for (String name : new String[]{"upper", "lower", "pastern", "knee_joint", "hock_joint", "paw", "toe0", "toe1", "toe2"}) root.getChild(PREFIX + name).visible = enabled;
        if (!enabled) return;
        float center = arm ? left ? 1 : -1 : 0;
        float start = arm ? -2 : 0;
        float length = end - start;
        float p = MathHelper.clamp(amount, 0, 1);
        var shoulder = new Vector3f(center, start, 0);
        var knee = new Vector3f(center, start + length * (.5f - .15f * p), (arm ? 1.3f : -2.25f) * p);
        var hock = new Vector3f(center, end - (2.5f + 2.8f * p), (arm ? -.4f : 1.9f) * p);
        var ankle = new Vector3f(center, end - 1.8f, -.4f * p);
        segment(root.getChild(PREFIX + "upper"), shoulder, knee, 1 + .12f * p);
        segment(root.getChild(PREFIX + "lower"), knee, hock, 1 - .18f * p);
        segment(root.getChild(PREFIX + "pastern"), hock, ankle, 1 - .25f * p);
        var kneeCap=root.getChild(PREFIX+"knee_joint");kneeCap.setPivot(knee.x,knee.y,knee.z);kneeCap.pitch=kneeCap.yaw=kneeCap.roll=0;
        var hockCap=root.getChild(PREFIX+"hock_joint");hockCap.setPivot(hock.x,hock.y,hock.z);hockCap.pitch=hockCap.yaw=hockCap.roll=0;
        var paw = root.getChild(PREFIX + "paw");
        paw.setPivot(ankle.x, ankle.y, ankle.z - .65f * p); paw.pitch = paw.yaw = paw.roll = 0;
        paw.xScale = 1 + .1f * p; paw.yScale = 1; paw.zScale = 1 + .15f * p;
        paw.visible=!outer;
        if(!outer)pad(paw,1.1f,1.15f,p);
        toes(root,p,outer);
    }

    private static void toes(ModelPart root, float progress, boolean outer) {
        var paw=root.getChild(PREFIX+"paw");
        float size=.8f+.2f*progress;
        for (int toe = 0; toe < 3; toe++) {
            var tip = root.getChild(PREFIX + "toe" + toe);
            // Medium-width toes extend from inside the paw along the mold's
            // three channels. Their spacing never collapses into the dividers.
            var offset=new Vector3f((toe-1)*1.45f*paw.xScale,1.8f-1.7f*size,
                    -2*paw.zScale+2.5f-1.55f*progress).rotateX(paw.pitch);
            tip.setPivot(paw.pivotX+offset.x,paw.pivotY+offset.y,paw.pivotZ+offset.z);
            tip.pitch=paw.pitch; tip.roll=tip.yaw=0;
            tip.xScale=tip.yScale=size; tip.zScale=1;
            tip.visible=!outer;
            if(!outer)pad(tip,1,1,progress);
        }
    }

    /** Pads begin at half width/depth, then grow, thicken and darken to their finished surface. */
    public static float padTint(float progress) { return MathHelper.lerp(MathHelper.clamp(progress,0,1),.98f,.72f); }
    private static void pad(ModelPart parent,float finalWidth,float finalDepth,float progress) {
        var pad=parent.getChild(PREFIX+"pad");
        float footprint=MathHelper.lerp(MathHelper.clamp(progress,0,1),.5f,1);
        pad.xScale=finalWidth*footprint/parent.xScale;pad.zScale=finalDepth*footprint/parent.zScale;
        pad.yScale=MathHelper.lerp(MathHelper.clamp(progress,0,1),.04f,1)/parent.yScale;
        ((Part)(Object)pad).sscExtras$padTint(padTint(progress));
    }

    private static void segment(ModelPart part, Vector3f from, Vector3f to, float width) {
        var direction = new Vector3f(to).sub(from);
        part.setPivot(from.x, from.y, from.z);
        part.pitch = (float)Math.atan2(direction.z, direction.y); part.yaw = part.roll = 0;
        part.xScale = part.zScale = width; part.yScale = direction.length() / 4;
    }

    public static Vector3f point(ModelPart root, String joint) {
        var part = root.getChild(PREFIX + joint);
        return localPoint(root, part.pivotX, part.pivotY, part.pivotZ).div(16);
    }
    /** A point in a joint's parent frame, in model pixels, with its exact posed transform. */
    public static Vector3f localPoint(ModelPart root, float x, float y, float z) {
        var point = new Vector3f(x * root.xScale, y * root.yScale, z * root.zScale);
        point.rotateX(root.pitch).rotateY(root.yaw).rotateZ(root.roll);
        return point.add(root.pivotX, root.pivotY, root.pivotZ);
    }
    public static ModelPart joint(ModelPart root, String name) { return root.getChild(PREFIX + name); }
}
