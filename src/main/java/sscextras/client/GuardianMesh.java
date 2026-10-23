package sscextras.client;

import sscextras.client.RiggedMesh.Part;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Vector3f;
import sscextras.guardian.RuneboundGuardian;
import java.io.IOException;
import java.util.*;

/** Rigid armor and a continuous, articulated tail core preserve the authored topology and UVs. */
public final class GuardianMesh {
    public static final List<String> PLAYER_JOINTS = AuthoredPlayerModel.PLAYER_JOINTS;
    public record Motion(boolean corePresent, boolean hideHead, float age, float limbAngle, float limbDistance, boolean statue,
            FormTailPhysics.Drag drag, boolean flying) {
        public Motion(boolean corePresent, boolean hideHead, float age, float limbAngle, float limbDistance, boolean statue) {
            this(corePresent, hideHead, age, limbAngle, limbDistance, statue, FormTailPhysics.Drag.NONE);
        }
        public Motion(boolean corePresent, boolean hideHead, float age, float limbAngle, float limbDistance, boolean statue,
                FormTailPhysics.Drag drag) {
            this(corePresent, hideHead, age, limbAngle, limbDistance, statue, drag, false);
        }
        float wingRoll() { return flying && !statue ? -.1f + .25f * (float)Math.sin(age * .3f) : 0; }
        float wingYaw(int side) {
            if (statue) return 0;
            float fold = (float)Math.sin(age * .06f) * .018f
                    + (float)Math.sin(limbAngle * .6662f) * Math.min(limbDistance, 1) * .02f
                    + drag.vertical() * .075f;
            return drag.horizontal() * .075f + side * fold;
        }
        float tailYaw(int joint) {
            return statue ? 0 : (float)Math.sin(age * .075f - joint * .55f) * (joint == 0 ? .12f : .07f)
                    + (float)Math.sin(limbAngle * .6662f - joint * .55f) * Math.min(limbDistance, 1) * (joint == 0 ? .06f : .035f)
                    + drag.horizontal() * (joint == 0 ? .18f : .10f / (1 + joint * .15f));
        }
        float tailPitch(int joint) {
            return statue ? 0 : (float)Math.sin(age * .045f - joint * .45f) * (joint == 0 ? .025f : .018f)
                    - drag.vertical() * (joint == 0 ? .12f : .07f / (1 + joint * .15f));
        }
    }
    private static final GuardianMesh[] MODELS = new GuardianMesh[4];
    private static final class Bone {
        String name, parent;
        float[] pivot;
        int tailIndex = -1;
        List<Part> parts = new ArrayList<>();
        List<Bone> children = new ArrayList<>();
    }
    private final List<Bone> roots = new ArrayList<>();
    private final Map<String, Bone> bones = new HashMap<>();
    private final Map<String, Vector3f> sizes = new HashMap<>();
    private final Map<String, Float> limbBottoms = new HashMap<>();
    private final Map<String, List<Part>> branches = new HashMap<>();
    private final List<Bone> tailBones = new ArrayList<>();
    private final Identifier[] textures, emission;
    private final int stage;

    public Vector3f pivot(String name) { return new Vector3f(bones.get(name).pivot); }
    private List<Part> branchParts(String name) {
        return branches.computeIfAbsent(name, key -> {
            var result=new ArrayList<Part>();collect(bones.get(key),result);return List.copyOf(result);
        });
    }
    private static void collect(Bone bone,List<Part> into) {
        into.addAll(bone.parts);for(var child:bone.children)collect(child,into);
    }
    public float limbBottom(String owner) {
        return limbBottoms.computeIfAbsent(owner, name -> {
            float bottom = pivot(name).y;
            for (var bone : bones.values()) if (name.equals(owner(bone)))
                for (var part : bone.parts) for (int i = 1; i < part.vertices().length; i += 8)
                    bottom = Math.max(bottom, part.vertices()[i]);
            return bottom;
        });
    }
    public Vector3f[] bounds(String name) { return RiggedMesh.bounds(bones.get(name).parts); }
    public Vector3f size(String name) {
        if (sizes.containsKey(name)) return sizes.get(name);
        var box = bounds(name);
        var size = box == null ? new Vector3f() : box[1].sub(box[0]);
        sizes.put(name, size);
        return size;
    }

    private String owner(Bone bone) {
        while (bone != null) {
            if (PLAYER_JOINTS.contains(bone.name)) return bone.name;
            bone = bones.get(bone.parent);
        }
        return null;
    }

    public void renderPlayer(MatrixStack matrices, VertexConsumerProvider buffers, int light, int overlay,
            GuardianPlayerModel model, boolean active, boolean weathered, float red, float green, float blue,
            float alpha, String arm, int equipment, Motion motion) {
        matrices.push();
        if (arm != null) {
            var shoulder = pivot(arm);
            matrices.translate((arm.equals("bipedLeftArm") ? 5 : -5) / 16f, 2 / 16f, 0);
            float scale = new float[]{1, .94f, .82f, .65f}[stage];
            matrices.scale(scale, scale, scale);
            matrices.translate(-shoulder.x, -shoulder.y, -shoulder.z);
        }
        for (var bone : bones.values()) {
            String owner = owner(bone);
            if (owner == null || arm != null && !owner.equals(arm)) continue;
            if (!model.joint(owner).visible || motion.hideHead && owner.equals("bipedHead")) continue;
            matrices.push();
            model.transform(matrices, owner);
            if (bone.tailIndex >= 0 && sscextras.player.PlayerStance.quadrupedal(model.wearer())) {
                var base=pivot("tail_0");matrices.translate(base.x,base.y,base.z);
                matrices.multiply(RotationAxis.POSITIVE_X.rotation(-model.body.pitch));
                matrices.translate(-base.x,-base.y,-base.z);
            }
            rotateWing(bone, matrices, motion);
            MatrixStack.Entry[] tailTransforms = null;
            if (bone.tailIndex >= 0) {
                for (int i = 0; i <= bone.tailIndex; i++) rotateTail(tailBones.get(i), matrices, motion);
                if (bone.tailIndex == 0) {
                    tailTransforms = new MatrixStack.Entry[tailBones.size()];
                    for (int i = 0; i < tailBones.size(); i++) {
                        if (i > 0) rotateTail(tailBones.get(i), matrices, motion);
                        tailTransforms[i] = matrices.peek(); matrices.push();
                    }
                    for (int i = 0; i < tailBones.size(); i++) matrices.pop();
                }
            }
            for (var source : bone.parts) {
                var part = TrainerGuardianRig.deform(model, model.wearer(), owner, source);
                if (part.core() && !motion.corePresent) continue;
                var texture = stage == 3 ? permanentTexture(weathered, active || part.core(), false) : textures[part.texture()];
                if (model.petrified()) texture = PetrificationClient.texture(texture);
                var layer = alpha < 1 ? RenderLayer.getEntityTranslucent(texture) : RenderLayer.getEntityCutoutNoCull(texture);
                emit(part, matrices, tailTransforms, buffers.getBuffer(layer), light, red, green, blue, alpha, overlay);
                if (!model.petrified() && (active || part.core() && motion.corePresent) && alpha == 1 && emission[part.texture()] != null) {
                    var glow = stage == 3 ? permanentTexture(weathered, true, true) : emission[part.texture()];
                    emit(part, matrices, tailTransforms, buffers.getBuffer(RenderLayer.getEyes(glow)), LightmapTextureManager.MAX_LIGHT_COORDINATE, 1);
                }
            }
            adornments(bone, matrices, buffers, light, equipment);
            boolean hand = bone.name.equals("left_hand") || bone.name.equals("right_hand");
            boolean foot = bone.name.equals("left_foot") || bone.name.equals("right_foot");
            if ((hand || foot) && !sscextras.trainer.QuadrupedalTrainer.equipped(model.wearer(), hand
                    ? sscextras.trainer.QuadrupedalTrainer.HANDS : sscextras.trainer.QuadrupedalTrainer.FEET, true).isEmpty())
                TrainerMeshMolds.render(branchParts(bone.name), matrices, buffers, light, point -> TrainerGuardianRig.map(model, model.wearer(), owner, point));
            boolean armBand=bone.name.endsWith("Arm")||bone.name.endsWith("_forearm");
            boolean legBand=bone.name.endsWith("Leg")||bone.name.endsWith("_shin");
            if((armBand||legBand)&&!sscextras.trainer.QuadrupedalTrainer.equipped(model.wearer(),armBand
                    ?sscextras.trainer.QuadrupedalTrainer.HANDS:sscextras.trainer.QuadrupedalTrainer.FEET,true).isEmpty())
                TrainerMeshMolds.band(bone.parts,matrices,buffers,light,point->TrainerGuardianRig.map(model,model.wearer(),owner,point));
            if((bone.name.endsWith("Arm")||bone.name.endsWith("Leg"))&&!sscextras.trainer.QuadrupedalTrainer.equipped(model.wearer(),armBand
                    ?sscextras.trainer.QuadrupedalTrainer.HANDS:sscextras.trainer.QuadrupedalTrainer.FEET,true).isEmpty()) {
                String side=bone.name.contains("Left")?"left_":"right_";
                var lower=bones.get(side+(armBand?"forearm":"shin"));
                if(lower!=null)TrainerMeshMolds.rails(bone.parts,lower.parts,branchParts(side+(armBand?"hand":"foot")),
                        matrices,buffers,light,point->TrainerGuardianRig.map(model,model.wearer(),owner,point));
            }
            matrices.pop();
        }
        matrices.pop();
    }

    private GuardianMesh(int stage) throws IOException {
        this.stage = stage;
        var data = RiggedMesh.load(RuneboundGuardian.id("guardian/stage_" + stage + ".json"),
                RuneboundGuardian.id("textures/guardian/"));
        textures = data.textures; emission = data.emission;
        for (var source : data.bones.values()) {
            var bone = new Bone(); bone.name = source.name(); bone.parent = source.parent();
            bone.pivot = new float[]{source.pivot().x, source.pivot().y, source.pivot().z};
            bone.parts.addAll(source.parts());
            if (bone.name.startsWith("tail_")) bone.tailIndex = Integer.parseInt(bone.name.substring(5));
            bones.put(bone.name, bone);
        }
        for (var bone : bones.values()) {
            if (bone.parent == null) roots.add(bone); else bones.get(bone.parent).children.add(bone);
        }
        for (int i = 0; bones.containsKey("tail_" + i); i++) tailBones.add(bones.get("tail_" + i));
    }
    public static void reload() { Arrays.fill(MODELS, null); }
    public static GuardianMesh get(int stage) {
        if (MODELS[stage] == null) {
            try { MODELS[stage] = new GuardianMesh(stage); }
            catch (IOException exception) { throw new IllegalStateException("Missing Guardian model " + stage, exception); }
        }
        return MODELS[stage];
    }
    public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, PlayerEntityModel<?> pose,
            boolean active, boolean weathered, float alpha, String arm) {
        render(matrices, buffers, light, pose, active, weathered, alpha, arm, 0);
    }
    public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, PlayerEntityModel<?> pose,
            boolean active, boolean weathered, float alpha, String arm, int equipment) {
        render(matrices, buffers, light, pose, active, weathered, alpha, arm, equipment, new Motion(active, false, 0, 0, 0, true));
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
        MatrixStack.Entry[] tailTransforms = null;
        rotateWing(bone, matrices, motion);
        if (bone.tailIndex >= 0) {
            rotateTail(bone, matrices, motion);
            if (bone.tailIndex == 0) {
                tailTransforms = new MatrixStack.Entry[tailBones.size()];
                matrices.push();
                for (int i = 0; i < tailBones.size(); i++) {
                    if (i > 0) rotateTail(tailBones.get(i), matrices, motion);
                    tailTransforms[i] = matrices.peek();
                    matrices.push();
                }
                for (int i = 0; i <= tailBones.size(); i++) matrices.pop();
            }
        }
        for (var mesh : bone.parts) {
            if (mesh.core() && !motion.corePresent) continue;
            Identifier texture = textures[mesh.texture()];
            if (stage == 3) texture = permanentTexture(weathered, active || mesh.core(), false);
            var layer = alpha < 1 ? RenderLayer.getEntityTranslucent(texture) : RenderLayer.getEntityCutoutNoCull(texture);
            emit(mesh, matrices, tailTransforms, buffers.getBuffer(layer), light, alpha);
            if ((active || mesh.core() && motion.corePresent) && alpha == 1 && emission[mesh.texture()] != null) {
                var glow = stage == 3 ? permanentTexture(weathered, true, true) : emission[mesh.texture()];
                emit(mesh, matrices, tailTransforms, buffers.getBuffer(RenderLayer.getEyes(glow)), LightmapTextureManager.MAX_LIGHT_COORDINATE, 1);
            }
        }
        adornments(bone, matrices, buffers, light, equipment);
        for (var child : bone.children) draw(child, matrices, buffers, light, pose, active, weathered, alpha, equipment, motion);
        matrices.pop();
    }
    private void adornments(Bone bone, MatrixStack matrices, VertexConsumerProvider buffers, int light, int equipment) {
        boolean wrist = (equipment & 1) != 0 && (bone.name.equals("left_hand") || bone.name.equals("right_hand"));
        boolean ankle = (equipment & 2) != 0 && (bone.name.equals("left_foot") || bone.name.equals("right_foot"));
        boolean collar = (equipment & 4) != 0 && bone.name.equals(stage == 3 ? "neck" : "bipedHead");
        if (wrist || ankle || collar) {
            GuardianAdornmentMesh.render("stage_" + stage + "/" + (collar ? "collar" : bone.name), matrices, buffers, light);
        }
    }
    private static void rotateWing(Bone bone, MatrixStack matrices, Motion motion) {
        if (!bone.name.equals("wing_left_0") && !bone.name.equals("wing_right_0")) return;
        var p = bone.pivot;
        matrices.translate(p[0], p[1], p[2]);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(motion.wingYaw(bone.name.equals("wing_left_0") ? 1 : -1)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(motion.wingRoll() * (bone.name.equals("wing_left_0") ? 1 : -1)));
        matrices.translate(-p[0], -p[1], -p[2]);
    }
    private static void rotateTail(Bone bone, MatrixStack matrices, Motion motion) {
        var p = bone.pivot; matrices.translate(p[0], p[1], p[2]);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(motion.tailYaw(bone.tailIndex)));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(motion.tailPitch(bone.tailIndex)));
        matrices.translate(-p[0], -p[1], -p[2]);
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
    private static void emit(Part mesh, MatrixStack matrices, MatrixStack.Entry[] tailTransforms, VertexConsumer consumer, int light, float alpha) {
        emit(mesh, matrices, tailTransforms, consumer, light, alpha < 1 ? .45f : 1, 1, 1, alpha, OverlayTexture.DEFAULT_UV);
    }
    private static void emit(Part mesh, MatrixStack matrices, MatrixStack.Entry[] tailTransforms, VertexConsumer consumer,
            int light, float red, float green, float blue, float alpha, int overlay) {
        RiggedMesh.emit(mesh, matrices, tailTransforms, consumer, light, red, green, blue, alpha, overlay);
    }
}
