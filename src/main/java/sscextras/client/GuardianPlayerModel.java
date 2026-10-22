package sscextras.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import org.joml.Vector3f;
import sscextras.guardian.GuardianEquipment;
import sscextras.guardian.RuneboundGuardian;

/** The Guardian is the player's body model; its joint positions also drive equipment. */
public final class GuardianPlayerModel extends AuthoredPlayerModel {
    private final int stage;
    private AbstractClientPlayerEntity player;
    private VertexConsumerProvider buffers;
    private GuardianMesh.Motion motion;
    private FormTailPhysics.Drag tailDrag = FormTailPhysics.Drag.NONE;

    public GuardianPlayerModel(int stage) {
        super(PlayerEntityModel.getTexturedModelData(Dilation.NONE, false).getRoot().createPart(64, 64));
        this.stage = stage;
    }

    public void prepare(AbstractClientPlayerEntity player, VertexConsumerProvider buffers) {
        this.player = player;
        this.buffers = buffers;
    }

    @Override public void animate(float tickDelta) {
        animateTail(tickDelta);
    }
    @Override public float renderScale() { return stage == 3 ? 1 / .9375f : 1; }

    public void animateTail(float tickDelta) {
        var state = RuneboundGuardian.state(player);
        if (state.pedestalSleeping || state.deactivated || state.detached || sscextras.petrification.Petrification.frozen(player)) {
            FormTailPhysics.reset(player);
            tailDrag = FormTailPhysics.Drag.NONE;
        } else tailDrag = FormTailPhysics.sample(player, tickDelta);
    }

    public void clear() { player = null; buffers = null; tailDrag = FormTailPhysics.Drag.NONE; }
    public GuardianMesh mesh() { return GuardianMesh.get(stage); }
    public int stage() { return stage; }
    public AbstractClientPlayerEntity wearer() { return player; }
    public boolean petrified() { return player != null && sscextras.petrification.Petrification.replaceTexture(player); }

    public ModelPart joint(String name) {
        return switch (name) {
            case "bipedBody" -> body;
            case "bipedHead" -> head;
            case "bipedLeftArm" -> leftArm;
            case "bipedRightArm" -> rightArm;
            case "bipedLeftLeg" -> leftLeg;
            case "bipedRightLeg" -> rightLeg;
            default -> null;
        };
    }

    @Override public void setAngles(AbstractClientPlayerEntity player, float limbAngle, float limbDistance,
            float age, float headYaw, float headPitch) {
        super.setAngles(player, limbAngle, limbDistance, age, headYaw, headPitch);
        var state = RuneboundGuardian.state(player);
        boolean statue = state.pedestalSleeping || state.deactivated;
        if (statue) {
            for (var name : GuardianMesh.PLAYER_JOINTS) {
                var part = joint(name);
                part.pitch = part.yaw = part.roll = 0;
            }
        }
        float bodyY = statue ? 0 : body.pivotY;
        float legY = statue ? 0 : leftLeg.pivotY - 12;
        float legZ = statue ? 0 : leftLeg.pivotZ;
        for (var name : GuardianMesh.PLAYER_JOINTS) {
            var part = joint(name);
            var rest = mesh().pivot(name).mul(16);
            part.setPivot(rest.x, rest.y, rest.z);
            var size = mesh().size(name);
            part.xScale = size.x / (name.equals("bipedBody") || name.equals("bipedHead") ? .5f : .25f);
            part.yScale = name.equals("bipedHead") ? size.y / .5f : 1;
            part.zScale = size.z / (name.equals("bipedHead") ? .5f : .25f);
        }
        body.pivotY += bodyY;
        body.yScale = mesh().size("bipedBody").y / .75f;
        for (var name : new String[]{"bipedHead", "bipedLeftArm", "bipedRightArm"}) {
            var part = joint(name);
            var point = posedPoint("bipedBody", mesh().pivot(name));
            part.setPivot(point.x * 16, point.y * 16, point.z * 16);
        }
        for (var part : new ModelPart[]{leftLeg, rightLeg}) {
            part.pivotY += legY;
            part.pivotZ += legZ;
        }
        TrainerGuardianRig.pose(this, player, limbAngle, limbDistance, statue);
        TrainerLimbModel.apply(this, player);
        hat.copyTransform(head); jacket.copyTransform(body);
        leftSleeve.copyTransform(leftArm); rightSleeve.copyTransform(rightArm);
        leftPants.copyTransform(leftLeg); rightPants.copyTransform(rightLeg);
        var client = MinecraftClient.getInstance();
        boolean hideHead = player == client.getCameraEntity() && client.options.getPerspective().isFirstPerson()
                && client.currentScreen == null;
        motion = new GuardianMesh.Motion(stage < 3 || state.coreId != null, hideHead, age, limbAngle, limbDistance, statue, tailDrag,
                state.flying() && !sscextras.petrification.Petrification.frozen(player));
    }

    public void transform(MatrixStack matrices, String name) {
        var part = joint(name);
        var rest = mesh().pivot(name);
        matrices.translate(part.pivotX / 16f, part.pivotY / 16f, part.pivotZ / 16f);
        matrices.multiply(new org.joml.Quaternionf().rotationZYX(part.roll, part.yaw, part.pitch));
        matrices.translate(-rest.x, -rest.y, -rest.z);
    }

    public Vector3f posedPoint(String name, Vector3f rest) {
        var matrices = new MatrixStack();
        transform(matrices, name);
        return matrices.peek().getPositionMatrix().transformPosition(rest);
    }

    @Override public void setArmAngle(Arm arm, MatrixStack matrices) {
        boolean left = arm == Arm.LEFT;
        transform(matrices, left ? "bipedLeftArm" : "bipedRightArm");
        var hand = mesh().pivot(left ? "left_hand" : "right_hand");
        // The held-item feature supplies its own grip rotation and vanilla hand offset.
        matrices.translate(hand.x + (left ? -1 : 1) / 16f, hand.y + .15f - .625f, hand.z - .03f + .125f);
    }

    @Override public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay,
            float red, float green, float blue, float alpha) {
        if (player == null || RuneboundGuardian.state(player).detached) return;
        var state = RuneboundGuardian.state(player);
        mesh().renderPlayer(matrices, buffers, light, overlay, this,
                stage < 2 || state.energy > 0 && !state.deactivated, state.weatherTicks >= 72000,
                red, green, blue, alpha, null, GuardianEquipment.visuals(player), motion);
    }

    public void renderHand(MatrixStack matrices, int light, boolean left) {
        sneaking = riding = false;
        handSwingProgress = 0;
        setVisible(true);
        setAngles(player, 0, 0, 0, 0, 0);
        var arm = left ? leftArm : rightArm;
        arm.pitch = arm.yaw = arm.roll = 0;
        var state = RuneboundGuardian.state(player);
        mesh().renderPlayer(matrices, buffers, light, net.minecraft.client.render.OverlayTexture.DEFAULT_UV, this,
                stage < 2 || state.energy > 0 && !state.deactivated, state.weatherTicks >= 72000,
                1, 1, 1, 1, left ? "bipedLeftArm" : "bipedRightArm", GuardianEquipment.visuals(player), motion);
    }
}
