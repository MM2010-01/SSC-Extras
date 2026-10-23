package sscextras.client;

import java.util.*;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import sscextras.petrification.Petrification;
import sscextras.petrification.PetrificationPose;

/** Freeze native rigs using stable model paths persisted in the creature's component. */
public final class PetrificationModelPose {
    private static Scope current;
    private PetrificationModelPose() { }
    private static float[] read(ModelPart p) {
        return new float[]{p.pivotX,p.pivotY,p.pivotZ,p.pitch,p.yaw,p.roll,p.xScale,p.yScale,p.zScale};
    }
    private static void apply(ModelPart p, float[] v) {
        p.setPivot(v[0],v[1],v[2]); p.setAngles(v[3],v[4],v[5]);
        p.xScale=v[6]; p.yScale=v[7]; p.zScale=v[8];
    }
    public static final class Scope implements AutoCloseable {
        private final Scope previous = current;
        private final LivingEntity entity;
        private final Map<ModelPart,float[]> originals = new IdentityHashMap<>();
        private final Map<ModelPart,float[]> playerParts = new IdentityHashMap<>();
        private Pass pass;
        private Scope(LivingEntity entity) { this.entity = entity; current = this; }
        @Override public void close() {
            originals.forEach(PetrificationModelPose::apply);
            current = previous;
            if (entity != null) {
                var state = Petrification.state(entity);
                if (state.submitPose()) {
                    var buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                    buffer.writeVarInt(entity.getId()); buffer.writeUuid(state.poseToken()); buffer.writeNbt(state.pose().write());
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(PetrificationPose.PACKET, buffer);
                }
            }
        }
    }
    public static final class Pass implements AutoCloseable {
        private final Scope scope = current;
        private final Pass previous;
        private final String name;
        private final Set<ModelPart> parts = Collections.newSetFromMap(new IdentityHashMap<>());
        private int roots;
        private Pass(String name) {
            this.name = name; previous = scope == null ? null : scope.pass;
            if (scope != null) scope.pass = this;
        }
        @Override public void close() { if (scope != null) scope.pass = previous; }
    }
    public static Pass pass(String name) { return new Pass(name); }
    public static Scope begin(Entity entity) {
        return new Scope(entity instanceof LivingEntity living && Petrification.poseLocked(living) ? living : null);
    }
    public static void apply(ModelPart root) {
        if (current == null || current.entity == null || current.pass == null || current.pass.parts.contains(root)) return;
        if (current.entity instanceof net.minecraft.entity.player.PlayerEntity) return;
        // Traverse before drawing: optimized renderers can bypass child render calls.
        applyTree(root, "part/" + current.pass.name + "/" + current.pass.roots++);
    }
    /** Capture the player's actual animated rig before camera mods temporarily hide or offset its parts. */
    public static void playerModel(net.minecraft.client.render.entity.model.PlayerEntityModel<?> model) {
        if (current == null || current.entity == null) return;
        var parts = new ModelPart[]{model.head,model.hat,model.body,model.jacket,model.leftArm,model.leftSleeve,
                model.rightArm,model.rightSleeve,model.leftLeg,model.leftPants,model.rightLeg,model.rightPants};
        for (int i=0;i<parts.length;i++) {
            var part=parts[i]; current.originals.computeIfAbsent(part,PetrificationModelPose::read);
            var value=Petrification.state(current.entity).pose().retain("part/body/player/"+i,read(part));
            current.playerParts.put(part,value); apply(part,value);
        }
        if (model instanceof AuthoredPlayerModel authored) authored.poseJoints().forEach((name, part) -> {
            current.originals.computeIfAbsent(part, PetrificationModelPose::read);
            var value = Petrification.state(current.entity).pose().retain("part/body/authored/" + name, read(part));
            current.playerParts.put(part, value); apply(part, value);
        });
    }
    /** SSC copies negated native positions into GeoBones; later camera offsets remain transient. */
    public static net.minecraft.util.math.Vec3d playerRenderOffset(ModelPart part) {
        var value=current==null?null:current.playerParts.get(part);
        return value==null?net.minecraft.util.math.Vec3d.ZERO
                :new net.minecraft.util.math.Vec3d(value[0]-part.pivotX,value[1]-part.pivotY,value[2]-part.pivotZ);
    }
    private static void applyTree(ModelPart part, String key) {
        if (!current.pass.parts.add(part)) return;
        current.originals.computeIfAbsent(part, PetrificationModelPose::read);
        apply(part, Petrification.state(current.entity).pose().retain(key, read(part)));
        var children = ((sscextras.mixin.PetrificationModelChildrenAccessor)(Object)part).petrificationChildren();
        int index = 0;
        for (var name : new TreeSet<>(children.keySet())) applyTree(children.get(name), key + "/" + index++);
    }
    /** Retain renderer-level body motion (for example slime squashing) in model-local space. */
    public static void transform(String pass, MatrixStack matrices, Runnable original) {
        if (current == null || current.entity == null) { original.run(); return; }
        var entry = matrices.peek();
        var beforePosition = new org.joml.Matrix4f(entry.getPositionMatrix());
        var beforeNormal = new org.joml.Matrix3f(entry.getNormalMatrix());
        original.run();
        float[] value = new float[25];
        new org.joml.Matrix4f(beforePosition).invert().mul(entry.getPositionMatrix()).get(value);
        new org.joml.Matrix3f(beforeNormal).invert().mul(entry.getNormalMatrix()).get(value, 16);
        var saved = Petrification.state(current.entity).pose().retain("matrix/" + pass, value);
        entry.getPositionMatrix().set(beforePosition).mul(new org.joml.Matrix4f().set(saved));
        entry.getNormalMatrix().set(beforeNormal).mul(new org.joml.Matrix3f().set(java.util.Arrays.copyOfRange(saved,16,25)));
    }
}
