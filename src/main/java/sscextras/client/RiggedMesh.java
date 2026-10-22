package sscextras.client;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;

/** Saved mesh data shared by authored player rigs. Pose and texture policy belong to the caller. */
public final class RiggedMesh {
    public record Part(int texture, boolean core, float[] vertices, int[] tailJoints) { }
    public record Bone(String name, String parent, Vector3f pivot, List<Part> parts) { }
    public final Map<String, Bone> bones;
    public final Identifier[] textures, emission;

    private RiggedMesh(Map<String, Bone> bones, Identifier[] textures, Identifier[] emission) {
        this.bones = Collections.unmodifiableMap(bones); this.textures = textures; this.emission = emission;
    }

    public static RiggedMesh load(Identifier source, Identifier textureDirectory) throws IOException {
        var resources = MinecraftClient.getInstance().getResourceManager();
        JsonObject data;
        try (var reader = resources.getResourceOrThrow(source).getReader()) {
            data = JsonParser.parseReader(reader).getAsJsonObject();
        }
        var names = data.getAsJsonArray("textures");
        var textures = new Identifier[names.size()]; var emission = new Identifier[names.size()];
        for (int i = 0; i < names.size(); i++) {
            textures[i] = new Identifier(textureDirectory.getNamespace(), textureDirectory.getPath() + names.get(i).getAsString());
            var glow = new Identifier(textures[i].toString().replace(".png", "_emission.png"));
            if (resources.getResource(glow).isPresent()) emission[i] = glow;
        }
        var bones = new LinkedHashMap<String, Bone>();
        for (var value : data.getAsJsonArray("bones")) {
            var object = value.getAsJsonObject(); var parts = new ArrayList<Part>();
            for (var entry : object.getAsJsonArray("parts")) {
                var part = entry.getAsJsonObject(); int[] joints = null;
                if (part.has("tail_joints")) {
                    var array = part.getAsJsonArray("tail_joints"); joints = new int[array.size()];
                    for (int i = 0; i < joints.length; i++) joints[i] = array.get(i).getAsInt();
                }
                parts.add(new Part(part.get("texture").getAsInt(), part.get("core").getAsBoolean(),
                        floats(part.getAsJsonArray("vertices")), joints));
            }
            String name = object.get("name").getAsString();
            String parent = object.get("parent").isJsonNull() ? null : object.get("parent").getAsString();
            if (bones.put(name, new Bone(name, parent, new Vector3f(floats(object.getAsJsonArray("pivot"))), List.copyOf(parts))) != null)
                throw new IOException("Duplicate rig bone in " + source + ": " + name);
        }
        for (var bone : bones.values()) if (bone.parent() != null && !bones.containsKey(bone.parent()))
            throw new IOException("Missing parent in " + source + ": " + bone.parent());
        return new RiggedMesh(bones, textures, emission);
    }

    private static float[] floats(JsonArray array) {
        float[] result = new float[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsFloat();
        return result;
    }

    public static void emit(Part mesh, MatrixStack matrices, MatrixStack.Entry[] perVertexJoints,
            VertexConsumer consumer, int light, float red, float green, float blue, float alpha, int overlay) {
        float[] v = mesh.vertices();
        if (mesh.tailJoints() != null) {
            var p = new Vector3f(); var n = new Vector3f();
            for (int i = 0; i < v.length; i += 8) {
                var joint = perVertexJoints[mesh.tailJoints()[i / 8]];
                joint.getPositionMatrix().transformPosition(v[i], v[i+1], v[i+2], p);
                joint.getNormalMatrix().transform(v[i+5], v[i+6], v[i+7], n);
                consumer.vertex(p.x, p.y, p.z).color(red, green, blue, alpha)
                        .texture(v[i+3], v[i+4]).overlay(overlay).light(light).normal(n.x, n.y, n.z).next();
            }
            return;
        }
        var position = matrices.peek().getPositionMatrix(); var normal = matrices.peek().getNormalMatrix();
        for (int i = 0; i < v.length; i += 8) consumer.vertex(position, v[i], v[i+1], v[i+2])
                .color(red, green, blue, alpha).texture(v[i+3], v[i+4]).overlay(overlay)
                .light(light).normal(normal, v[i+5], v[i+6], v[i+7]).next();
    }
}
