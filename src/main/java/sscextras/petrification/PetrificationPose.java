package sscextras.petrification;

import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.entity.LivingEntity;
import net.fabricmc.fabric.api.networking.v1.*;

/** Bounded, cosmetic model transforms. The status effect remains the gameplay authority. */
public final class PetrificationPose {
    public static final net.minecraft.util.Identifier PACKET = Petrification.id("frozen_pose");
    public static final int MAX_BYTES = 60000;
    private final Map<String, float[]> values = new TreeMap<>();
    private int bytes;

    public boolean isEmpty() { return values.isEmpty(); }
    /** Renderer body matrices include absolute yaw; rotate them with the placed body, leaving bones and scale untouched. */
    public void turn(float degrees) {
        float radians = (float)Math.toRadians(-degrees);
        for (var name : new String[]{"matrix/body", "matrix/stable_drake"}) {
            var saved = values.get(name); if (saved == null) continue;
            var position = new org.joml.Matrix4f().rotationY(radians).mul(new org.joml.Matrix4f().set(saved));
            var normal = new org.joml.Matrix3f().rotationY(radians).mul(new org.joml.Matrix3f().set(java.util.Arrays.copyOfRange(saved, 16, 25)));
            position.get(saved); normal.get(saved, 16);
        }
    }
    public float[] retain(String key, float[] value) {
        var saved = values.get(key);
        if (saved != null && saved.length == value.length) return saved.clone();
        if (valid(key, value) && values.size() < 512 && bytes + size(key, value) <= MAX_BYTES) {
            values.put(key, value.clone()); bytes += size(key, value);
        }
        return value;
    }
    private static int size(String key, float[] value) { return 16 + key.length() * 3 + value.length * 4; }
    private static boolean valid(String key, float[] value) {
        if (key.length() > 192 || !(key.startsWith("part/") || key.startsWith("bone/") || key.startsWith("matrix/"))
                || value.length != (key.startsWith("matrix/") ? 25 : 9)) return false;
        for (float n : value) if (!Float.isFinite(n) || Math.abs(n) > 1000000) return false;
        return true;
    }
    public NbtCompound write() {
        var tag = new NbtCompound();
        values.forEach((key, value) -> {
            var list = new NbtList(); for (float n : value) list.add(NbtFloat.of(n)); tag.put(key, list);
        });
        return tag;
    }
    /** Invalid or excessive payloads are rejected as a whole, including data from old saves. */
    public static PetrificationPose read(NbtCompound tag) {
        var pose = new PetrificationPose();
        if (tag == null || tag.getSize() > 512) return pose;
        for (var key : tag.getKeys()) {
            var list = tag.getList(key, NbtElement.FLOAT_TYPE);
            float[] value = new float[list.size()];
            for (int i = 0; i < value.length; i++) value[i] = list.getFloat(i);
            if (!valid(key, value) || pose.bytes + size(key, value) > MAX_BYTES) return new PetrificationPose();
            pose.retain(key, value);
        }
        return pose;
    }
    public boolean hasBody() {
        return values.keySet().stream().anyMatch(key -> key.startsWith("part/body/") || key.startsWith("bone/"));
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(PACKET, (server, player, handler, buffer, response) -> {
            if (buffer.readableBytes() > MAX_BYTES || buffer.readableBytes() < 17) return;
            int id = buffer.readVarInt(); var token = buffer.readUuid(); var pose = read(buffer.readNbt());
            server.execute(() -> {
                if (!(player.getWorld().getEntityById(id) instanceof LivingEntity body)) return;
                if (body != player && !PlayerLookup.tracking(body).contains(player)) return;
                Petrification.state(body).acceptPose(token, pose);
            });
        });
    }
}
