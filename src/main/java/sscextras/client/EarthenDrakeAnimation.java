package sscextras.client;

import com.google.gson.JsonObject;
import mod.azure.azurelib.cache.object.GeoBone;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class EarthenDrakeAnimation extends DefaultModelAnimationSystem {
    private int stage;

    @Override public void loadConfig(JsonObject config) {
        super.loadConfig(config);
        stage = config == null ? 0 : config.get("stage").getAsInt();
    }

    @Override public void processAnimation(FormRenderer formRenderer, FormModel model, PlayerEntityRenderer renderer,
            PlayerEntity player, float limbAngle, float limbDistance, float tickDelta, float age, float headYaw, float headPitch) {
        super.processAnimation(formRenderer, model, renderer, player, limbAngle, limbDistance, tickDelta, age, headYaw, headPitch);
        if (stage == 2) {
            var body = model.getCachedGeoBone("bipedBody");
            anchorHip(model.getCachedGeoBone("bipedLeftLeg"), body);
            anchorHip(model.getCachedGeoBone("bipedRightLeg"), body);
        }
        for (int i = 0; i < 5; i++) {
            var tail = model.getCachedGeoBone("tail_" + i);
            if (tail == null) continue;
            // Authored rearward: translate SSC's downward-tail sway onto the yaw axis.
            tail.setRotY(tail.getRotZ());
            tail.setRotZ(0);
            if (i == 0 && stage == 2) {
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
                    ? .10f + Math.abs(MathHelper.sin(age * 1.4f)) * .12f : .015f;
            jaw.setRotX(opening);
        }
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
