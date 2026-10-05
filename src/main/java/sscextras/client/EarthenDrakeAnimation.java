package sscextras.client;

import com.google.gson.JsonObject;
import mod.azure.azurelib.cache.object.GeoBone;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import sscextras.drake.EarthenDrake;

public final class EarthenDrakeAnimation extends DefaultModelAnimationSystem {
    private int stage;

    @Override public void loadConfig(JsonObject config) {
        super.loadConfig(config);
        stage = config == null ? 0 : config.get("stage").getAsInt();
    }

    @Override public void processAnimation(FormRenderer formRenderer, FormModel model, PlayerEntityRenderer renderer,
            PlayerEntity player, float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
        super.processAnimation(formRenderer, model, renderer, player, limbAngle, limbDistance, tickDelta, age, headYaw, headPitch);
        if (sscextras.drake.DrakeSoulbinding.shoeing(player)) {
            for (var name : new String[]{"bipedHead", "bipedBody", "bipedLeftArm", "bipedRightArm", "bipedLeftLeg", "bipedRightLeg"})
                model.resetBone(name);
            if (stage < 3) {
                var body = renderer.getModel();
                copyShoeingPose(model.getCachedGeoBone("bipedLeftArm"), body.leftArm);
                copyShoeingPose(model.getCachedGeoBone("bipedRightArm"), body.rightArm);
                copyShoeingPose(model.getCachedGeoBone("bipedLeftLeg"), body.leftLeg);
                copyShoeingPose(model.getCachedGeoBone("bipedRightLeg"), body.rightLeg);
            } else {
                for (String name : new String[]{"bipedLeftArm", "bipedLeftLeg"}) model.getCachedGeoBone(name).setRotZ(-1.15f);
                for (String name : new String[]{"bipedRightArm", "bipedRightLeg"}) model.getCachedGeoBone(name).setRotZ(1.15f);
                model.getCachedGeoBone("bipedHead").setRotX(.45f);
            }
            int paw = sscextras.drake.DrakeSoulbinding.shoeingPaw(player);
            if (stage == 3 && paw >= 0) {
                String limb = "biped" + (paw % 2 == 0 ? "Left" : "Right") + (paw < 2 ? "Arm" : "Leg");
                var bone = model.getCachedGeoBone(limb);
                bone.setRotZ(paw % 2 == 0 ? -.35f : .35f);
                bone.setRotX(.18f);
            }
            return;
        }
        boolean struggling = sscextras.drake.DrakeSoulbinding.restrained(player);
        float phase = age * .075f;
        float effort = struggling ? Math.max(0, MathHelper.sin(phase)) : 0;
        effort *= effort;
        float pull = struggling ? MathHelper.sin(phase * 2) * effort : 0;
        if (struggling) {
            rotate(model.getCachedGeoBone("bipedHead"), .045f * effort, .10f * pull);
            rotate(model.getCachedGeoBone("bipedLeftArm"), .035f * effort + .025f * pull, 0);
            rotate(model.getCachedGeoBone("bipedRightArm"), .035f * effort - .025f * pull, 0);
        }
        boolean earlySoul = stage < 2 && sscextras.drake.DrakeSoulbinding.bound(player);
        if (stage == 2) {
            var body = model.getCachedGeoBone("bipedBody");
            anchorHip(model.getCachedGeoBone("bipedLeftLeg"), body);
            anchorHip(model.getCachedGeoBone("bipedRightLeg"), body);
        }
        for (int i = 0; i < 5; i++) {
            var tail = model.getCachedGeoBone("tail_" + i);
            if (tail == null) continue;
            // Authored rearward: translate SSC's downward-tail sway onto the yaw axis.
            tail.setRotY(tail.getRotZ() + (sscextras.drake.DrakeAttention.beingPetted(player)
                    ? sscextras.drake.DrakeAttention.wag(age, i) : 0)
                    + (struggling ? .02f * effort * MathHelper.sin(phase - i * .45f) : 0));
            tail.setRotZ(0);
            if (i == 0 && (stage == 2 || earlySoul)) {
                var body = model.getCachedGeoBone("bipedBody");
                if (body != null) {
                    tail.setPosZ((body.getPivotZ() - tail.getPivotZ()) * MathHelper.sin(body.getRotX()));
                    // Cancel the torso tilt before applying sway, so yaw stays sideways.
                    var rotation = new Quaternionf().rotationX(-body.getRotX())
                            .rotateY(tail.getRotY()).rotateX(tail.getRotX()).getEulerAnglesZYX(new Vector3f());
                    tail.setRotX(rotation.x);
                    tail.setRotY(rotation.y);
                    tail.setRotZ(rotation.z);
                }
            }
        }
        var jaw = model.getCachedGeoBone("jaw");
        if (jaw != null) {
            float opening = player.isUsingItem() && player.getActiveItem().isFood()
                    ? .10f + Math.abs(MathHelper.sin(age * 1.4f)) * .12f : .015f + .02f * effort;
            jaw.setRotX(opening);
        }
    }

    public static void poseShoeingBody(PlayerEntityModel<?> model, PlayerEntity player) {
        if (!sscextras.drake.DrakeSoulbinding.shoeing(player) || EarthenDrake.stage(player) >= 3) return;
        int paw = sscextras.drake.DrakeSoulbinding.shoeingPaw(player);
        model.head.setPivot(0, 0, 0); model.body.setPivot(0, 0, 0);
        model.leftArm.setPivot(5, 2, 0); model.rightArm.setPivot(-5, 2, 0);
        model.leftLeg.setPivot(1.9f, 12, 0); model.rightLeg.setPivot(-1.9f, 12, 0);
        model.head.pitch = model.head.yaw = model.head.roll = 0;
        model.body.pitch = model.body.yaw = model.body.roll = 0;
        model.leftArm.pitch = -1.35f - (paw == 0 ? .18f : 0);
        model.rightArm.pitch = -1.35f - (paw == 1 ? .18f : 0);
        model.leftArm.yaw = .15f; model.rightArm.yaw = -.15f;
        model.leftArm.roll = model.rightArm.roll = 0;
        model.leftLeg.pitch = -.3f - (paw == 2 ? .55f : 0);
        model.rightLeg.pitch = -.3f - (paw == 3 ? .55f : 0);
        model.leftLeg.yaw = .12f; model.rightLeg.yaw = -.12f;
        model.leftLeg.roll = model.rightLeg.roll = 0;
        model.leftSleeve.copyTransform(model.leftArm); model.rightSleeve.copyTransform(model.rightArm);
        model.leftPants.copyTransform(model.leftLeg); model.rightPants.copyTransform(model.rightLeg);
        model.hat.copyTransform(model.head); model.jacket.copyTransform(model.body);
    }

    private static void copyShoeingPose(GeoBone bone, ModelPart limb) {
        if (bone == null) return;
        bone.setRotX(limb.pitch); bone.setRotY(-limb.yaw); bone.setRotZ(-limb.roll);
    }

    private static void rotate(GeoBone bone, float pitch, float yaw) {
        if (bone == null) return;
        bone.setRotX(bone.getRotX() + pitch);
        bone.setRotY(bone.getRotY() + yaw);
    }

    private static void anchorHip(GeoBone leg, GeoBone body) {
        if (leg == null || body == null) return;
        // Follow the animated pelvis, retaining SSC's native leg swing and state blending.
        float x = leg.getPivotX() - body.getPivotX();
        float y = leg.getPivotY() - body.getPivotY();
        float z = leg.getPivotZ() - body.getPivotZ();
        var hip = new Vector3f(x, y, z - 1).rotateX(body.getRotX()).rotateY(body.getRotY()).rotateZ(body.getRotZ());
        leg.setPosX(body.getPosX() + hip.x - x);
        leg.setPosY(body.getPosY() + hip.y - y);
        leg.setPosZ(body.getPosZ() + hip.z - z);
    }

    @Override public GeoBone processAnimationFirstPerson(GeoBone bone, FormRenderer formRenderer, FormModel model,
            PlayerEntityRenderer renderer, PlayerEntity player, ModelPart arm, ModelPart sleeve) {
        bone = super.processAnimationFirstPerson(bone, formRenderer, model, renderer, player, arm, sleeve);
        if (stage == 3 && bone != null) {
            boolean right = arm == renderer.getModel().rightArm;
            model.translatePositionForBone(right ? rightArmGeoBoneID : leftArmGeoBoneID,
                    new Vec3d(right ? -1.586667 : 1.586667, 12.442667, -3.413333));
            for (String part : new String[]{right ? "right_forearm" : "left_forearm",
                    right ? "right_front_paw" : "left_front_paw"}) {
                model.setPositionForBone(part, Vec3d.ZERO);
                model.setRotationForBone(part, Vec3d.ZERO);
            }
        }
        return bone;
    }
}
