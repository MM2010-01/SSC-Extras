package sscextras.client;

import java.io.IOException;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.joml.*;
import sscextras.golem.DrakeGolem;
import sscextras.golem.DrakeGolemBody;

/** One 28-joint player skeleton drives saved stone geometry, equipment and first-person paws. */
public final class DrakeGolemPlayerModel extends AuthoredPlayerModel {
    private final RiggedMesh mesh;
    private final Map<String, ModelPart> joints = new LinkedHashMap<>();
    private final Map<String, Matrix4f> transforms = new LinkedHashMap<>();
    private AbstractClientPlayerEntity player;
    private VertexConsumerProvider buffers;
    private float delta;
    private FormTailPhysics.Drag tailDrag = FormTailPhysics.Drag.NONE;

    public DrakeGolemPlayerModel() { this(load()); }
    private DrakeGolemPlayerModel(RiggedMesh mesh) {
        super(emptyRoot()); this.mesh = mesh;
        for (var bone : mesh.bones.values()) joints.put(bone.name(), switch (bone.name()) {
            case "bipedBody" -> body; case "bipedHead" -> head;
            case "bipedLeftArm" -> leftArm; case "bipedRightArm" -> rightArm;
            case "bipedLeftLeg" -> leftLeg; case "bipedRightLeg" -> rightLeg;
            default -> new ModelPart(List.of(), Map.of());
        });
        applyPose(Map.of(), 0);
    }
    private static RiggedMesh load() {
        try { return RiggedMesh.load(DrakeGolem.id("golem/drake_golem.json"), DrakeGolem.id("textures/golem/")); }
        catch (IOException exception) { throw new IllegalStateException("Missing Drake Golem player rig", exception); }
    }
    private static ModelPart emptyRoot() {
        var children = new LinkedHashMap<String, ModelPart>();
        for (String name : List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg",
                "ear","cloak","left_sleeve","right_sleeve","left_pants","right_pants","jacket"))
            children.put(name, new ModelPart(List.of(), Map.of()));
        return new ModelPart(List.of(), children);
    }
    @Override public void prepare(AbstractClientPlayerEntity player, VertexConsumerProvider buffers) { this.player = player; this.buffers = buffers; }
    @Override public void animate(float delta) {
        this.delta = delta;
        if (player.isInPose(net.minecraft.entity.EntityPose.SLEEPING) || sscextras.petrification.Petrification.poseLocked(player)) {
            FormTailPhysics.reset(player);
            tailDrag = FormTailPhysics.Drag.NONE;
        } else tailDrag = FormTailPhysics.sample(player, delta);
    }
    @Override public void clear() { player = null; buffers = null; delta = 0; tailDrag = FormTailPhysics.Drag.NONE; }
    public FormTailPhysics.Drag tailDrag() { return tailDrag; }
    @Override public float renderScale() { return 1 / .9375f; }
    public RiggedMesh mesh() { return mesh; }
    public ModelPart joint(String name) { return joints.get(name); }
    @Override public Map<String, ModelPart> poseJoints() { return java.util.Collections.unmodifiableMap(joints); }
    public int jointCount() { return joints.size(); }
    public Vector3f rest(String name) { return new Vector3f(mesh.bones.get(name).pivot()); }
    public Vector3f posedPoint(String name, Vector3f point) { return transforms.get(name).transformPosition(point); }

    /** Local animation deltas are resolved through the saved hierarchy once per pose.
     * Native player parts are the same world-space joint objects used for rendering. */
    public void applyPose(Map<String, Vector3f> rotations, float bodyDrop) {
        transforms.clear();
        for (var bone : mesh.bones.values()) {
            var matrix = bone.parent() == null ? new Matrix4f() : new Matrix4f(transforms.get(bone.parent()));
            if (bone.name().equals("bipedBody")) matrix.translate(0, bodyDrop, 0);
            var pivot = bone.pivot(); var angle = rotations.getOrDefault(bone.name(), new Vector3f());
            matrix.translate(pivot).rotateZYX(angle.z, angle.y, angle.x).translate(-pivot.x,-pivot.y,-pivot.z);
            transforms.put(bone.name(), matrix);
            var part = joints.get(bone.name()); var position = matrix.transformPosition(new Vector3f(pivot));
            // JOML 1.10.5's quaternion ZYX extraction loses pitch with combined
            // yaw/pitch; the matrix extraction round-trips this exact joint basis.
            var rotation = matrix.getEulerAnglesZYX(new Vector3f());
            part.setPivot(position.x*16, position.y*16, position.z*16);
            part.pitch = rotation.x; part.yaw = rotation.y; part.roll = rotation.z;
            part.xScale = part.yScale = part.zScale = 1;
        }
        // These are armor fit dimensions, never a second body or a visual body scale.
        head.xScale = 1.08f; head.yScale = .62f; head.zScale = 1.25f;
        body.xScale = 1.85f; body.yScale = .85f; body.zScale = 3.6f;
        leftArm.xScale = rightArm.xScale = 1.3f; leftArm.zScale = rightArm.zScale = 1.45f;
        leftLeg.xScale = rightLeg.xScale = 1.3f; leftLeg.zScale = rightLeg.zScale = 1.45f;
        hat.copyTransform(head); jacket.copyTransform(body);
        leftSleeve.copyTransform(leftArm); rightSleeve.copyTransform(rightArm);
        leftPants.copyTransform(leftLeg); rightPants.copyTransform(rightLeg);
    }
    @Override public void setAngles(AbstractClientPlayerEntity player, float limbAngle, float limbDistance,
            float age, float headYaw, float headPitch) {
        DrakeGolemAnimation.pose(this, player, limbAngle, limbDistance, age, headYaw, headPitch, delta, handSwingProgress);
    }
    public void transform(MatrixStack matrices, String name) {
        // Rotation is read from the actual joint; armor and attachments see this same pose.
        var part = joints.get(name); var pivot = mesh.bones.get(name).pivot();
        matrices.translate(part.pivotX/16f, part.pivotY/16f, part.pivotZ/16f);
        matrices.multiply(new Quaternionf().rotationZYX(part.roll, part.yaw, part.pitch));
        matrices.translate(-pivot.x,-pivot.y,-pivot.z);
    }
    public void atJoint(MatrixStack matrices, String name) {
        transform(matrices,name); var pivot=rest(name); matrices.translate(pivot.x,pivot.y,pivot.z);
    }
    private String owner(String name) {
        var bone=mesh.bones.get(name);
        while (bone != null) {
            if (PLAYER_JOINTS.contains(bone.name())) return bone.name();
            bone=mesh.bones.get(bone.parent());
        }
        return null;
    }
    @Override public void render(MatrixStack matrices, VertexConsumer ignored, int light, int overlay,
            float red, float green, float blue, float alpha) {
        renderMesh(matrices,light,overlay,red,green,blue,alpha,null);
    }
    private void renderMesh(MatrixStack matrices, int light, int overlay, float red, float green, float blue, float alpha, String arm) {
        if (player==null || buffers==null) return;
        var state=DrakeGolemBody.get(player);
        boolean weathered=state!=null && state.weathered(), active=state==null || state.runesOn();
        boolean core=state==null || state.corePresent();
        String stem="textures/golem/drake_golem"+(weathered?"_weathered":"")+(active?"":"_runes_off");
        var color=DrakeGolem.id(stem+".png"); var glow=DrakeGolem.id(stem+"_emission.png");
        var client=MinecraftClient.getInstance();
        boolean hideHead=arm==null && player==client.getCameraEntity() && client.options.getPerspective().isFirstPerson() && client.currentScreen==null;
        for (var bone:mesh.bones.values()) {
            var owner=owner(bone.name());
            if (owner==null || !joints.get(owner).visible || arm!=null && !owner.equals(arm) || hideHead && owner.equals("bipedHead")) continue;
            matrices.push(); transform(matrices,bone.name());
            for (var part:bone.parts()) {
                if (part.core() && !core) continue;
                var layer=alpha<1?RenderLayer.getEntityTranslucent(color):RenderLayer.getEntityCutoutNoCull(color);
                RiggedMesh.emit(part,matrices,null,buffers.getBuffer(layer),light,red,green,blue,alpha,overlay);
                if (alpha==1) RiggedMesh.emit(part,matrices,null,buffers.getBuffer(RenderLayer.getEyes(glow)),
                        LightmapTextureManager.MAX_LIGHT_COORDINATE,1,1,1,1,overlay);
            }
            matrices.pop();
        }
    }
    @Override public void setArmAngle(Arm arm, MatrixStack matrices) {
        // Native item renderers which request an arm receive the articulated paw.
        atJoint(matrices,arm==Arm.LEFT?"left_front_paw":"right_front_paw");
    }
    @Override public void renderHand(MatrixStack matrices, int light, boolean left) {
        sneaking=riding=false; handSwingProgress=0; setVisible(true);
        DrakeGolemAnimation.pose(this,player,0,0,player.age,0,0,0,0);
        String arm=left?"bipedLeftArm":"bipedRightArm"; var pivot=rest(arm);
        matrices.push(); matrices.translate((left?5:-5)/16f,2/16f,0); matrices.scale(.75f,.75f,.75f);
        matrices.translate(-pivot.x,-pivot.y,-pivot.z);
        renderMesh(matrices,light,OverlayTexture.DEFAULT_UV,1,1,1,1,arm); matrices.pop();
    }
}
