package sscextras.client;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import sscextras.guardian.RuneboundGuardian;
import java.io.IOException;
import java.util.*;

/** Rigid bone skinning keeps the authored triangle topology and texture coordinates intact. */
public final class GuardianMesh {
    public record Motion(boolean corePresent, boolean hideHead, float tailYaw, float tailPitch) { }
    private static final GuardianMesh[] MODELS = new GuardianMesh[4];
    private record Part(int texture, boolean core, float[] vertices) { }
    private static final class Bone {
        String name, parent;
        float[] pivot;
        List<Part> parts = new ArrayList<>();
        List<Bone> children = new ArrayList<>();
    }
    private final List<Bone> roots = new ArrayList<>();
    private final Map<String, Bone> bones = new HashMap<>();
    private final Identifier[] textures, emission;
    private final int stage;

    private GuardianMesh(int stage) throws IOException {
        this.stage = stage;
        var resources = MinecraftClient.getInstance().getResourceManager();
        JsonObject data;
        try (var reader = resources.getResourceOrThrow(RuneboundGuardian.id("guardian/stage_" + stage + ".json")).getReader()) {
            data = JsonParser.parseReader(reader).getAsJsonObject();
        }
        var paths = data.getAsJsonArray("textures");
        textures = new Identifier[paths.size()]; emission = new Identifier[paths.size()];
        for (int i = 0; i < paths.size(); i++) {
            textures[i] = RuneboundGuardian.id("textures/guardian/" + paths.get(i).getAsString());
            var glow = new Identifier(textures[i].toString().replace(".png", "_emission.png"));
            if (resources.getResource(glow).isPresent()) emission[i] = glow;
        }
        for (var value : data.getAsJsonArray("bones")) {
            var object = value.getAsJsonObject(); var bone = new Bone();
            bone.name = object.get("name").getAsString();
            bone.parent = object.get("parent").isJsonNull() ? null : object.get("parent").getAsString();
            bone.pivot = floats(object.getAsJsonArray("pivot"));
            for (var entry : object.getAsJsonArray("parts")) {
                var part = entry.getAsJsonObject();
                bone.parts.add(new Part(part.get("texture").getAsInt(), part.get("core").getAsBoolean(), floats(part.getAsJsonArray("vertices"))));
            }
            bones.put(bone.name, bone);
        }
        for (var bone : bones.values()) {
            if (bone.parent == null) roots.add(bone); else bones.get(bone.parent).children.add(bone);
        }
    }
    private static float[] floats(JsonArray array) {
        float[] values = new float[array.size()];
        for (int i = 0; i < values.length; i++) values[i] = array.get(i).getAsFloat();
        return values;
    }
    public static void reload() { Arrays.fill(MODELS, null); }
    public static GuardianMesh get(int stage) {
        if (MODELS[stage] == null) {
            try { MODELS[stage] = new GuardianMesh(stage); }
            catch (IOException exception) { throw new IllegalStateException("Missing Guardian model " + stage, exception); }
        }
        return MODELS[stage];
    }
    public void hand(MatrixStack matrices, PlayerEntityModel<?> pose, boolean left) {
        if(stage==3) {matrices.translate(0,1.5,0);matrices.scale(1/.9375f,1/.9375f,1/.9375f);matrices.translate(0,-1.5,0);}
        var shoulder=bones.get(left?"bipedLeftArm":"bipedRightArm");
        var hand=bones.get(left?"left_hand":"right_hand");
        if(shoulder==null||hand==null)return;
        var arm=left?pose.leftArm:pose.rightArm;var p=shoulder.pivot;
        matrices.translate(p[0],p[1],p[2]);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(arm.roll));matrices.multiply(RotationAxis.POSITIVE_Y.rotation(arm.yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(arm.pitch));matrices.translate(-p[0],-p[1],-p[2]);
        matrices.translate(hand.pivot[0],hand.pivot[1]+.15,hand.pivot[2]-.03);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90));matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
    }
    public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, PlayerEntityModel<?> pose,
            boolean active, boolean weathered, float alpha, String arm) {
        render(matrices, buffers, light, pose, active, weathered, alpha, arm, 0);
    }
    public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, PlayerEntityModel<?> pose,
            boolean active, boolean weathered, float alpha, String arm, int equipment) {
        render(matrices, buffers, light, pose, active, weathered, alpha, arm, equipment, new Motion(active, false, 0, 0));
    }
    public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, PlayerEntityModel<?> pose,
            boolean active, boolean weathered, float alpha, String arm, int equipment, Motion motion) {
        matrices.push();
        if (arm != null) {
            var bone=bones.get(arm); boolean left=arm.equals("bipedLeftArm");
            matrices.translate((left?5:-5)/16f,2/16f,0);
            float scale=new float[]{1,.94f,.82f,.65f}[stage];matrices.scale(scale,scale,scale);
            matrices.translate(-bone.pivot[0],-bone.pivot[1],-bone.pivot[2]);
        } else if (stage == 3) { matrices.translate(0, 1.5, 0); matrices.scale(1/.9375f, 1/.9375f, 1/.9375f); matrices.translate(0, -1.5, 0); }
        if (arm == null) for (var root : roots) draw(root, matrices, buffers, light, pose, active, weathered, alpha, equipment, motion);
        else draw(bones.get(arm), matrices, buffers, light, pose, active, weathered, alpha, equipment, motion);
        matrices.pop();
    }
    private void draw(Bone bone, MatrixStack matrices, VertexConsumerProvider buffers, int light,
            PlayerEntityModel<?> pose, boolean active, boolean weathered, float alpha, int equipment, Motion motion) {
        if (bone == null || motion.hideHead && bone.name.equals("bipedHead")) return;
        matrices.push();
        ModelPart part = pose == null ? null : switch (bone.name) {
            case "bipedHead" -> pose.head;
            case "bipedLeftArm" -> pose.leftArm;
            case "bipedRightArm" -> pose.rightArm;
            case "bipedLeftLeg" -> pose.leftLeg;
            case "bipedRightLeg" -> pose.rightLeg;
            default -> null;
        };
        if (part != null) {
            var p = bone.pivot; matrices.translate(p[0], p[1], p[2]);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(part.roll));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(part.yaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(part.pitch));
            matrices.translate(-p[0], -p[1], -p[2]);
        }
        if (bone.name.equals("tail_0")) {
            var p = bone.pivot; matrices.translate(p[0], p[1], p[2]);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotation(motion.tailYaw));
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(motion.tailPitch));
            matrices.translate(-p[0], -p[1], -p[2]);
        }
        for (var mesh : bone.parts) {
            if (mesh.core && !motion.corePresent) continue;
            Identifier texture = textures[mesh.texture];
            if (stage == 3) texture = permanentTexture(weathered, active || mesh.core, false);
            var layer = alpha < 1 ? RenderLayer.getEntityTranslucent(texture) : RenderLayer.getEntityCutoutNoCull(texture);
            emit(mesh.vertices, matrices, buffers.getBuffer(layer), light, alpha);
            if ((active || mesh.core && motion.corePresent) && alpha == 1 && emission[mesh.texture] != null) {
                var glow = stage == 3 ? permanentTexture(weathered, true, true) : emission[mesh.texture];
                emit(mesh.vertices, matrices, buffers.getBuffer(RenderLayer.getEyes(glow)), LightmapTextureManager.MAX_LIGHT_COORDINATE, 1);
            }
        }
        boolean wrist = (equipment & 1) != 0 && (bone.name.equals("left_hand") || bone.name.equals("right_hand"));
        boolean ankle = (equipment & 2) != 0 && (bone.name.equals("left_foot") || bone.name.equals("right_foot"));
        boolean collar = (equipment & 4) != 0 && bone.name.equals(stage >= 2 ? "neck" : "bipedBody");
        if (wrist || ankle || collar) {
            GuardianAdornmentMesh.render("stage_" + stage + "/" + (collar ? "collar" : bone.name), matrices, buffers, light);
        }
        for (var child : bone.children) draw(child, matrices, buffers, light, pose, active, weathered, alpha, equipment, motion);
        matrices.pop();
    }
    public static void ring(MatrixStack matrices, VertexConsumerProvider buffers, int light, float[] center, float radius, float height) {
        ring(matrices, buffers, light, center, radius, radius, height);
    }
    public static void ring(MatrixStack matrices, VertexConsumerProvider buffers, int light, float[] center, float radius, float depth, float height) {
        var consumer = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(new Identifier("minecraft", "textures/block/oxidized_copper.png")));
        float x=center[0], y=center[1]-.035f, z=center[2], thickness=.035f;
        box(matrices,consumer,light,x-radius,y-height,z-depth,x+radius,y+height,z-depth+thickness);
        box(matrices,consumer,light,x-radius,y-height,z+depth-thickness,x+radius,y+height,z+depth);
        box(matrices,consumer,light,x-radius,y-height,z-depth+thickness,x-radius+thickness,y+height,z+depth-thickness);
        box(matrices,consumer,light,x+radius-thickness,y-height,z-depth+thickness,x+radius,y+height,z+depth-thickness);
    }
    private static void box(MatrixStack matrices, VertexConsumer consumer, int light, float x,float y,float z,float X,float Y,float Z) {
        float[][] points={{x,y,z},{X,y,z},{X,Y,z},{x,Y,z},{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{0,1,5,4},{3,7,6,2}};
        float[][] normals={{0,0,-1},{0,0,1},{-1,0,0},{1,0,0},{0,-1,0},{0,1,0}};
        float[][] uv={{0,0},{0,1},{1,1},{1,0}};
        for(int face=0;face<6;face++) for(int i=0;i<4;i++) {
            float[] p=points[faces[face][i]],n=normals[face];
            consumer.vertex(matrices.peek().getPositionMatrix(),p[0],p[1],p[2]).color(1f,1f,1f,1f).texture(uv[i][0],uv[i][1])
                    .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(matrices.peek().getNormalMatrix(),n[0],n[1],n[2]).next();
        }
    }
    public static Identifier permanentTexture(boolean weathered, boolean active, boolean glow) {
        return RuneboundGuardian.id("textures/guardian/runebound_guardian_" + (weathered ? "weathered_stone" : "stone")
                + (active ? "" : "_runes_off") + (glow ? "_emission" : "") + ".png");
    }
    private static void emit(float[] v, MatrixStack matrices, VertexConsumer consumer, int light, float alpha) {
        var position = matrices.peek().getPositionMatrix(); var normal = matrices.peek().getNormalMatrix();
        for (int i = 0; i < v.length; i += 8) consumer.vertex(position, v[i], v[i+1], v[i+2])
                .color(alpha < 1 ? .45f : 1, 1, 1, alpha).texture(v[i+3], v[i+4]).overlay(OverlayTexture.DEFAULT_UV)
                .light(light).normal(normal, v[i+5], v[i+6], v[i+7]).next();
    }
}
