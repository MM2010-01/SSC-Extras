package sscextras.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import sscextras.drake.EarthenDrake;
import sscextras.drake.DrakeSoulbinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;

public final class DrakeShoeModel {
    private static final Identifier TEXTURE = EarthenDrake.id("textures/entity/iron_drake_shoes.png");
    private final Map<String, ModelPart> parts = new HashMap<>();
    private final Map<String, Vector3f> grips = new HashMap<>();
    private final Map<String, Integer> paws = new HashMap<>();
    private record Grip(Vec3d position, int age) { }
    private static final Map<PlayerEntity, Grip[]> GRIPS = new WeakHashMap<>();

    public DrakeShoeModel(int stage) {
        var data = new ModelData();
        var names = new java.util.ArrayList<String>();
        for (boolean hand : new boolean[]{true, false}) for (int side : new int[]{-1, 1}) {
            float x, y, z, width, depth, height;
            if (stage == 3) {
                x = side * (hand ? 4.565f : 4.18f); y = .0f;
                z = hand ? 3.35f : -5.95f; width = hand ? 2.75f : 2.9f; depth = 2.85f; height = 1.4f;
            } else if (hand) {
                x = side * 6; y = stage == 0 ? 10.05f : stage == 1 ? 9.9f : 11.0f;
                z = -2.4f; width = stage == 0 ? 4.65f : 5.65f; depth = stage == 2 ? 4.8f : 4.75f;
                height = stage == 0 ? 3.5f : stage == 1 ? 5.65f : 3.6f;
            } else {
                x = side * (stage == 2 ? 4.65f : 2); y = -.08f; z = stage == 2 ? -.12f : -2.25f;
                width = stage == 2 ? 5.1f : 4.65f; depth = stage == 2 ? 4.45f : stage == 1 ? 5.8f : 4.65f;
                height = stage == 0 ? 2.2f : stage == 1 ? 3.0f : 2.2f;
            }
            String bone = stage < 3 ? "biped" + (side < 0 ? "Left" : "Right") + (hand ? "Arm" : "Leg")
                    : (side < 0 ? "left" : "@left@") + (hand ? "_front_paw" : "_hind_paw");
            x = -x;
            var iron = ModelPartBuilder.create().uv(0, 0);
            iron.cuboid(x - width / 2, y, z, width, .32f, depth)
                    .cuboid(x - width / 2, y, z, .3f, height, depth)
                    .cuboid(x + width / 2 - .3f, y, z, .3f, height, depth)
                    .cuboid(x - width / 2, y, z, width, height, .3f)
                    .cuboid(x - width / 2, y + height - .3f, z, width, .3f, depth)
                    .cuboid(x - width / 2, y, z + depth - .32f, width, height, .32f);
            float toeWidth = width / 3 - .14f;
            float toeHeight = stage == 3 ? 1.15f : hand ? stage == 1 ? 2.6f : 2.1f : 1.2f;
            float toeLength = stage == 3 ? 1.55f : hand && stage == 2 ? 2.3f : 1.65f;
            for (int toe = 0; toe < 3; toe++) iron.cuboid(x - width / 2 + .07f + toe * width / 3,
                    y + .06f, z + depth - .4f, toeWidth, toeHeight, toeLength);
            iron.uv(0, 32).cuboid(x - width * .27f, y - .12f, z + depth * .2f,
                    width * .54f, .2f, depth * .5f);
            for (int toe = 0; toe < 3; toe++) iron.cuboid(x - width / 2 + .16f + toe * width / 3,
                    y - .12f, z + depth - .3f, toeWidth - .18f, .2f, toeLength * .75f);
            float bandHeight = MathHelper.clamp(height * .2f, .35f, .65f);
            float bandY = y + height - bandHeight - .15f;
            iron.uv(0, 32)
                    .cuboid(x - width / 2 - .08f, bandY, z - .08f, .16f, bandHeight, depth + .16f)
                    .cuboid(x + width / 2 - .08f, bandY, z - .08f, .16f, bandHeight, depth + .16f)
                    .cuboid(x - width / 2 - .08f, bandY, z - .08f, width + .16f, bandHeight, .16f)
                    .cuboid(x - width / 2 - .08f, bandY, z + depth - .08f, width + .16f, bandHeight, .16f);
            data.getRoot().addChild(bone, iron, ModelTransform.NONE);
            grips.put(bone, new Vector3f(x / 16, (y + height * .75f) / 16, (z + depth / 2) / 16));
            paws.put(bone, (hand ? 0 : 2) + (side < 0 ? 0 : 1));
            names.add(bone);
        }
        var model = TexturedModelData.of(data, 64, 64).createModel();
        for (var child : names) parts.put(child, model.getChild(child));
    }

    public void captureGrip(PlayerEntity player, String bone, MatrixStack matrices) {
        if (!DrakeSoulbinding.shoeing(player)) { GRIPS.remove(player); return; }
        if (!net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderUtils.isRenderingInWorld) return;
        if (bone.equals("right_front_paw")) bone = "@left@_front_paw";
        if (bone.equals("right_hind_paw")) bone = "@left@_hind_paw";
        var local = grips.get(bone);
        if (local == null) return;
        var point = matrices.peek().getPositionMatrix().transformPosition(new Vector3f(local));
        point.mul(RenderSystem.getInverseViewRotationMatrix());
        var camera = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        GRIPS.computeIfAbsent(player, unused -> new Grip[4])[paws.get(bone)] = new Grip(camera.add(point.x, point.y, point.z), player.age);
    }

    public static Vec3d grip(IllagerEntity keeper) {
        boolean working = DrakeSoulbinding.role(keeper) == DrakeSoulbinding.SHOEING;
        for (var player : keeper.getWorld().getEntitiesByClass(PlayerEntity.class, keeper.getBoundingBox().expand(4), DrakeSoulbinding::shoeing)) {
            var points = GRIPS.get(player);
            if (points == null) continue;
            int paw = DrakeSoulbinding.shoeingPaw(working ? keeper : player);
            Vec3d closest = null;
            for (int i = 0; i < points.length; i++) {
                var point = points[i];
                if (point == null || player.age - point.age() > 2 || (working ? i != paw : i == paw)) continue;
                if (closest == null || keeper.squaredDistanceTo(point.position()) < keeper.squaredDistanceTo(closest)) closest = point.position();
            }
            if (closest != null) return closest;
        }
        return null;
    }

    public static void reach(ModelPart arm, IllagerEntity keeper, Vec3d target, float time) {
        float delta = MathHelper.clamp(time - keeper.age, 0, 1);
        var relative = target.subtract(keeper.getLerpedPos(delta));
        var point = new Vector3f((float)relative.x, (float)relative.y, (float)relative.z)
                .rotateY((MathHelper.lerpAngleDegrees(delta, keeper.prevBodyYaw, keeper.bodyYaw) - 180) * MathHelper.RADIANS_PER_DEGREE)
                .mul(-1, -1, 1);
        point.add(-arm.pivotX / 16, 1.501f - arm.pivotY / 16, -arm.pivotZ / 16);
        arm.pitch = (float)Math.atan2(point.z, Math.sqrt(point.x * point.x + point.y * point.y));
        arm.yaw = 0;
        arm.roll = (float)Math.atan2(-point.x, point.y);
        arm.yScale = MathHelper.clamp(point.length() / (9f / 16), .8f, 1.65f);
    }

    public void render(String bone, MatrixStack matrices, VertexConsumerProvider buffers, int light, boolean hands, boolean feet) {
        if (bone.equals("right_front_paw")) bone = "@left@_front_paw";
        if (bone.equals("right_hind_paw")) bone = "@left@_hind_paw";
        boolean hand = bone.endsWith("Arm") || bone.endsWith("front_paw");
        var part = parts.get(bone);
        if (part != null && (hand ? hands : feet))
            part.render(matrices, buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE)), light, OverlayTexture.DEFAULT_UV);
    }
}
