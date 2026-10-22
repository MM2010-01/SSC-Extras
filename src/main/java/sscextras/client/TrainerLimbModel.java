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
    public interface Part { void sscExtras$reshaped(boolean active); }
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
            limb.addChild(PREFIX + "paw", ModelPartBuilder.create().uv(u, v + 5)
                    .cuboid(-2, 0, -2, 4, 1.8f, 4, dilation), ModelTransform.NONE);
            for (int toe = 0; toe < 3; toe++) limb.addChild(PREFIX + "toe" + toe,
                    ModelPartBuilder.create().uv(u + 4, v + 8).cuboid(-.55f, 0, -2.1f, 1.1f, 1.1f, 2.1f, dilation), ModelTransform.NONE);
        }
    }

    public static boolean active(PlayerEntity player, boolean arms) {
        return QuadrupedalTrainer.reshapes(player, arms ? QuadrupedalTrainer.HANDS : QuadrupedalTrainer.FEET);
    }

    public static void apply(PlayerEntityModel<?> model, PlayerEntity player) {
        boolean quad = PlayerStance.quadrupedal(player);
        boolean ground = quad && !player.isSwimming() && !player.isFallFlying() && !player.hasVehicle() && !player.isSleeping();
        boolean hands = active(player, true), feet = active(player, false);
        // Native shoulders/hips keep their owner and animation; the new joints
        // grow beneath them. Cloth and skin receive the exact same articulation.
        for (int i = 0; i < 4; i++) {
            boolean arm = i < 2, left = i % 2 == 0;
            var root = arm ? left ? model.leftArm : model.rightArm : left ? model.leftLeg : model.rightLeg;
            var cloth = arm ? left ? model.leftSleeve : model.rightSleeve : left ? model.leftPants : model.rightPants;
            boolean enabled = arm ? hands : feet;
            float progress = QuadrupedalTrainer.progress(player, arm ? QuadrupedalTrainer.HANDS : QuadrupedalTrainer.FEET);
            float end = arm ? 10 : 12;
            float bottom = 24 - Math.max(0, root.pitch) * 4;
            if (ground) end = (bottom - root.pivotY - 1.8f + MathHelper.sin(root.pitch) * -1.2f * progress)
                    / Math.max(.5f, MathHelper.cos(root.pitch)) + 1.8f;
            reshape(root, arm, left, enabled, progress, end, ground);
            reshape(cloth, arm, left, enabled, progress, end, ground);
            if (enabled && ground) {
                // A planted foot's support surface remains level as the leg swings.
                root.getChild(PREFIX + "paw").pitch = -root.pitch;
                cloth.getChild(PREFIX + "paw").pitch = -root.pitch;
                for (int toe = 0; toe < 3; toe++) {
                    root.getChild(PREFIX + "toe" + toe).pitch = -root.pitch;
                    cloth.getChild(PREFIX + "toe" + toe).pitch = -root.pitch;
                    var tip=root.getChild(PREFIX+"toe"+toe);
                    tip.pivotY=(bottom-root.pivotY-1.1f+MathHelper.sin(root.pitch)*tip.pivotZ)/Math.max(.5f,MathHelper.cos(root.pitch));
                    cloth.getChild(PREFIX+"toe"+toe).pivotY=tip.pivotY;
                }
            }
        }
        if (QuadrupedalTrainer.neckLocked(player)) {
            model.head.pitch = QuadrupedalTrainer.pitch(player) * MathHelper.RADIANS_PER_DEGREE;
            model.hat.copyTransform(model.head);
        }
    }

    private static void reshape(ModelPart root, boolean arm, boolean left, boolean enabled, float amount, float end, boolean quad) {
        if (!root.hasChild(PREFIX + "upper")) return;
        ((Part)(Object)root).sscExtras$reshaped(enabled);
        for (String name : new String[]{"upper", "lower", "pastern", "knee_joint", "hock_joint", "paw", "toe0", "toe1", "toe2"}) root.getChild(PREFIX + name).visible = enabled;
        if (!enabled) return;
        float center = arm ? left ? 1 : -1 : 0;
        float start = arm ? -2 : 0;
        float length = end - start;
        float p = MathHelper.clamp(amount, 0, 1);
        var shoulder = new Vector3f(center, start, 0);
        var knee = new Vector3f(center, start + length * .43f, (arm ? 1.3f : -2.25f) * p);
        var hock = new Vector3f(center, end - 3.8f, (arm ? -.4f : 1.9f) * p);
        var ankle = new Vector3f(center, end - 1.8f, -.4f * p);
        segment(root.getChild(PREFIX + "upper"), shoulder, knee, 1 + .12f * p);
        segment(root.getChild(PREFIX + "lower"), knee, hock, 1 - .18f * p);
        segment(root.getChild(PREFIX + "pastern"), hock, ankle, 1 - .25f * p);
        var kneeCap=root.getChild(PREFIX+"knee_joint");kneeCap.setPivot(knee.x,knee.y,knee.z);kneeCap.pitch=kneeCap.yaw=kneeCap.roll=0;
        var hockCap=root.getChild(PREFIX+"hock_joint");hockCap.setPivot(hock.x,hock.y,hock.z);hockCap.pitch=hockCap.yaw=hockCap.roll=0;
        var paw = root.getChild(PREFIX + "paw");
        paw.setPivot(ankle.x, ankle.y, ankle.z - .8f * p); paw.pitch = paw.yaw = paw.roll = 0;
        paw.xScale = 1 + .25f * p; paw.yScale = 1; paw.zScale = 1 + .3f * p;
        for (int toe = 0; toe < 3; toe++) {
            var tip = root.getChild(PREFIX + "toe" + toe);
            tip.setPivot(center + (toe - 1) * 1.55f * p, end - 1.1f, ankle.z - 2.3f * p);
            tip.pitch = tip.roll = 0; tip.yaw = (toe - 1) * .10f * p;
            tip.xScale = tip.yScale = tip.zScale = p;
        }
    }

    private static void segment(ModelPart part, Vector3f from, Vector3f to, float width) {
        var direction = new Vector3f(to).sub(from);
        part.setPivot(from.x, from.y, from.z);
        part.pitch = (float)Math.atan2(direction.z, direction.y); part.yaw = part.roll = 0;
        part.xScale = part.zScale = width; part.yScale = direction.length() / 4;
    }

    public static Vector3f point(ModelPart root, String joint) {
        var part = root.getChild(PREFIX + joint);
        var point = new Vector3f(part.pivotX * root.xScale, part.pivotY * root.yScale, part.pivotZ * root.zScale);
        point.rotateX(root.pitch).rotateY(root.yaw).rotateZ(root.roll);
        return point.add(root.pivotX, root.pivotY, root.pivotZ).div(16);
    }
    public static ModelPart joint(ModelPart root, String name) { return root.getChild(PREFIX + name); }
}
