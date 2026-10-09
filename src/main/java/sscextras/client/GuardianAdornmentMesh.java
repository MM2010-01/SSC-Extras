package sscextras.client;

import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import sscextras.guardian.RuneboundGuardian;
import java.io.IOException;
import java.util.*;

public final class GuardianAdornmentMesh {
    private record Part(Identifier texture, float[] vertices) { }
    private static Map<String, List<Part>> meshes;
    private GuardianAdornmentMesh() { }
    public static void reload() { meshes = null; }
    public static void render(String name, MatrixStack matrices, VertexConsumerProvider buffers, int light) {
        if (meshes == null) load();
        var parts = meshes.get(name);
        if (parts == null) return;
        for (var part : parts) {
            var consumer = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(part.texture));
            float[] v = part.vertices;
            for (int i = 0; i < v.length; i += 8) consumer.vertex(matrices.peek().getPositionMatrix(), v[i], v[i+1], v[i+2])
                    .color(1f,1f,1f,1f).texture(v[i+3],v[i+4]).overlay(OverlayTexture.DEFAULT_UV).light(light)
                    .normal(matrices.peek().getNormalMatrix(),v[i+5],v[i+6],v[i+7]).next();
        }
    }
    private static void load() {
        try (var reader = MinecraftClient.getInstance().getResourceManager()
                .getResourceOrThrow(RuneboundGuardian.id("guardian/equipment.json")).getReader()) {
            var data = JsonParser.parseReader(reader).getAsJsonObject();
            var textures = data.getAsJsonArray("textures");
            var result = new HashMap<String, List<Part>>();
            for (var entry : data.getAsJsonObject("meshes").entrySet()) {
                var parts = new ArrayList<Part>();
                for (var element : entry.getValue().getAsJsonObject().getAsJsonArray("parts")) {
                    var part = element.getAsJsonObject(); var vertices = part.getAsJsonArray("vertices");
                    float[] values = new float[vertices.size()];
                    for (int i = 0; i < values.length; i++) values[i] = vertices.get(i).getAsFloat();
                    parts.add(new Part(new Identifier(textures.get(part.get("texture").getAsInt()).getAsString()), values));
                }
                result.put(entry.getKey(), parts);
            }
            meshes = result;
        } catch (IOException exception) { throw new IllegalStateException("Missing Guardian ornament meshes", exception); }
    }
}
