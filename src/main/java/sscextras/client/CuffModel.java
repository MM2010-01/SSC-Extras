package sscextras.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** A closed iron band with flush Standard Galactic Alphabet engravings. Dimensions are model pixels. */
public final class CuffModel {
    private static final Identifier TEXTURE = new Identifier("ssc-extras", "textures/entity/collar.png");
    private static final Identifier GLYPHS = new Identifier("minecraft", "textures/font/ascii_sga.png");
    private static final String[] INSCRIPTION = {"curse", "sup", "press", "ion"};
    private final ModelPart band, rims;
    private final float width, depth;
    private final boolean taming;

    public CuffModel(float width, float depth) {
        this(width, depth, false);
    }

    public CuffModel(float width, float depth, boolean taming) {
        this.width = width;
        this.depth = depth;
        this.taming = taming;
        var data = new ModelData();
        var root = data.getRoot();
        var body = ModelPartBuilder.create();
        ring(body, width, depth, -.8f, 1.6f, .3f);
        root.addChild("band", body, ModelTransform.NONE);
        var edges = ModelPartBuilder.create();
        ring(edges, width + .08f, depth + .08f, -.95f, .28f, .32f);
        ring(edges, width + .08f, depth + .08f, .67f, .28f, .32f);
        root.addChild("rims", edges, ModelTransform.NONE);
        var baked = TexturedModelData.of(data, 32, 32).createModel();
        band = baked.getChild("band"); rims = baked.getChild("rims");
    }

    private static void ring(ModelPartBuilder model, float width, float depth, float y, float height, float thick) {
        float x = width / 2, z = depth / 2;
        model.cuboid(-x - thick, y, -z - thick, width + 2 * thick, height, thick)
                .cuboid(-x - thick, y, z, width + 2 * thick, height, thick)
                .cuboid(-x - thick, y, -z, thick, height, depth)
                .cuboid(x, y, -z, thick, height, depth);
    }

    public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, boolean geoCoordinates) {
        render(matrices, buffers, light, geoCoordinates, taming ? 0xB342EB : 0x52BDC2);
    }

    public void render(MatrixStack matrices, VertexConsumerProvider buffers, int light, boolean geoCoordinates, int color) {
        var buffer = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        band.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, taming ? .09f : .57f, taming ? .08f : .62f, taming ? .13f : .67f, 1);
        rims.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, taming ? .28f : .88f, taming ? .24f : .92f, taming ? .34f : .94f, 1);
        buffer = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(GLYPHS));
        float pixel = Math.min(.15f, (Math.min(width, depth) + .4f) / 22);
        for (int side = 0; side < INSCRIPTION.length; side++) {
            matrices.push();
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((geoCoordinates ? 90 : -90) * side));
            matrices.translate(0, 0, -((side % 2 == 0 ? depth : width) / 2 + .3f) / 16);
            engrave(matrices.peek(), buffer, light, taming ? side % 2 == 0 ? "curse" : "rune" : INSCRIPTION[side], pixel, geoCoordinates, color);
            matrices.pop();
        }
    }

    private void engrave(MatrixStack.Entry pose, VertexConsumer buffer, int light, String text, float pixel, boolean geoCoordinates, int color) {
        int length = -1;
        for (int i = 0; i < text.length(); i++) length += glyphWidth(text.charAt(i)) + 1;
        float x = length * pixel / 2, y = -3.5f * pixel;
        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            int width = glyphWidth(letter);
            // A dark upper edge makes the cyan lettering read as a cut into the metal.
            glyph(pose, buffer, light, letter, width, x + .025f, y - .025f, -.002f, pixel, .20f, .28f, .30f, geoCoordinates);
            glyph(pose, buffer, taming ? light : net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE,
                    letter, width, x, y, -.004f, pixel, (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, geoCoordinates);
            x -= (width + 1) * pixel;
        }
    }

    private static int glyphWidth(char letter) {
        return switch (letter) {
            case 'c', 's' -> 2;
            case 'u', 'e' -> 5;
            case 'r', 'o', 'n' -> 4;
            case 'p' -> 3;
            case 'i' -> 1;
            default -> throw new IllegalArgumentException("Unsupported cuff inscription letter: " + letter);
        };
    }

    private static void glyph(MatrixStack.Entry pose, VertexConsumer buffer, int light, char letter, int width,
            float x, float y, float z, float pixel, float red, float green, float blue, boolean geoCoordinates) {
        float u = (letter % 16 * 8) / 128f, v = (letter / 16 * 8) / 128f;
        float right = x - width * pixel, bottom = y + 7 * pixel;
        vertex(pose, buffer, light, x, y, z, u, v, red, green, blue, geoCoordinates);
        vertex(pose, buffer, light, x, bottom, z, u, v + 7 / 128f, red, green, blue, geoCoordinates);
        vertex(pose, buffer, light, right, bottom, z, u + width / 128f, v + 7 / 128f, red, green, blue, geoCoordinates);
        vertex(pose, buffer, light, right, y, z, u + width / 128f, v, red, green, blue, geoCoordinates);
    }

    private static void vertex(MatrixStack.Entry pose, VertexConsumer buffer, int light, float x, float y, float z,
            float u, float v, float red, float green, float blue, boolean geoCoordinates) {
        if (geoCoordinates) y = -y;
        else x = -x;
        buffer.vertex(pose.getPositionMatrix(), x / 16, y / 16, z / 16).color(red, green, blue, 1)
                .texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(pose.getNormalMatrix(), 0, 0, -1).next();
    }
}
