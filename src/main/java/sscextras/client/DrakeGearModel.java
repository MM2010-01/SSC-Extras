package sscextras.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import sscextras.drake.EarthenDrake;

/** Equipment uses the same bone transforms as the visible body, including SSC's poses. */
public final class DrakeGearModel {
    public static final Identifier TEXTURE = EarthenDrake.id("textures/entity/drake_gear.png");
    private static final Identifier VANILLA_SADDLE_TEXTURE = EarthenDrake.id("textures/entity/drake_gear_vanilla.png");
    private final ModelPart reins, reinsMetal, blinkers, saddle, saddleMetal, chest, chestMetal, leftClaws, rightClaws;

    public DrakeGearModel(int stage) {
        this(stage, false);
    }

    public DrakeGearModel(int stage, boolean helmet) {
        boolean mature = stage == 3;
        ModelData data = new ModelData();
        var root = data.getRoot();
        var straps = ModelPartBuilder.create().uv(0, 16);
        var rings = ModelPartBuilder.create().uv(16, 0);
        float cheekX, cheekZ, bitX, bitZ, bitY;
        if (mature) {
            straps.cuboid(-2.95f, 16.3f, 6.1f, 5.9f, .35f, .6f)
                    .cuboid(-2.95f, 13.2f, 6.1f, .4f, 3.45f, .6f)
                    .cuboid(2.55f, 13.2f, 6.1f, .4f, 3.45f, .6f)
                    .cuboid(-2.95f, 13.2f, 6.1f, .4f, .5f, 2.7f)
                    .cuboid(2.55f, 13.2f, 6.1f, .4f, .5f, 2.7f);
            snoutBand(straps, 2.2f, 11.37f, 14.8f, 11.5f);
            cheekX = 2.75f; cheekZ = 8.55f; bitX = 2.375f; bitZ = 11.8f; bitY = 13.2f;
        } else {
            float headHalf = stage == 2 ? 4.2f : helmet ? 5.1f : 4.65f;
            float top = stage == 2 ? 32.15f : helmet ? 33.15f : 32.65f;
            float face = helmet ? 5.15f : 4.15f;
            straps.cuboid(-headHalf - .35f, 25.4f, -headHalf, .35f, .5f, headHalf + face)
                    .cuboid(headHalf, 25.4f, -headHalf, .35f, .5f, headHalf + face)
                    .cuboid(-headHalf - .35f, 25.4f, -headHalf, 2 * (headHalf + .35f), .5f, .35f)
                    .cuboid(-headHalf - .35f, top, -1, 2 * (headHalf + .35f), .35f, .65f)
                    .cuboid(-headHalf - .35f, 25.4f, -1, .35f, top - 25.05f, .65f)
                    .cuboid(headHalf, 25.4f, -1, .35f, top - 25.05f, .65f);
            cheekX = headHalf + .175f; cheekZ = face - .2f; bitY = 25.4f;
            if (stage < 0) {
                straps.cuboid(-headHalf - .35f, 25.4f, face - .2f, 2 * (headHalf + .35f), .5f, .4f);
                bitX = cheekX; bitZ = face;
            } else {
                float halfNose = stage == 2 ? 2.95f : stage == 1 ? 2.35f : 1.85f;
                float noseTop = stage == 2 ? 27 : stage == 1 ? 26.4f : 25.9f;
                float bandZ = stage == 2 ? 6.9f : stage == 1 ? 5.25f : 4.3f;
                snoutBand(straps, halfNose, 24.45f, noseTop, bandZ);
                bitX = halfNose + .175f; bitZ = bandZ + .3f;
            }
        }
        var reinParts = root.addChild("reins", straps, ModelTransform.NONE);
        for (int side : new int[]{-1, 1}) {
            float dx = side * (bitX - cheekX), dz = bitZ - cheekZ;
            float length = (float)Math.sqrt(dx * dx + dz * dz);
            reinParts.addChild("cheek_" + side, ModelPartBuilder.create().uv(0, 16)
                    .cuboid(-.2f, 0, -.2f, .4f, .5f, length + .4f),
                    ModelTransform.of(side * cheekX, bitY, cheekZ, 0, (float)Math.atan2(dx, dz), 0));
            rings.cuboid(side * bitX - .25f, bitY - .2f, bitZ - .4f, .5f, .9f, .8f);
        }
        root.addChild("rings", rings, ModelTransform.NONE);
        var shields = ModelPartBuilder.create().uv(0, 16);
        float shieldX = mature ? 2.75f : stage == 2 ? 4.35f : helmet ? 5.25f : 4.8f;
        float shieldY = mature ? 13.3f : 26.2f;
        float shieldZ = mature ? 6.25f : -.8f;
        for (int side : new int[]{-1, 1}) {
            shields.cuboid(side * shieldX - .25f, shieldY, shieldZ, .5f, mature ? 3.1f : 4.1f, mature ? 3.5f : 6);
        }
        root.addChild("blinkers", shields, ModelTransform.NONE);
        var leather = ModelPartBuilder.create().uv(0, 16);
        var metal = ModelPartBuilder.create().uv(16, 0);
        if (mature) {
            leather.cuboid(-4.15f, 10.45f, -4.7f, 8.3f, .55f, 7.2f)
                    .cuboid(-2.45f, 10.95f, -4.15f, .65f, 2.65f, 6.1f)
                    .cuboid(1.8f, 10.95f, -4.15f, .65f, 2.65f, 6.1f)
                    .cuboid(-3.1f, 13.6f, -4.15f, 6.2f, .6f, 6.1f)
                    .cuboid(-3.15f, 14, 1.3f, 6.3f, .9f, .8f)
                    .cuboid(-3.15f, 14, -4.4f, 6.3f, .8f, .75f)
                    .cuboid(-3.8f, 4.4f, -1.4f, .5f, 6.7f, 1)
                    .cuboid(3.3f, 4.4f, -1.4f, .5f, 6.7f, 1)
                    .cuboid(-3.8f, 4.4f, -1.4f, 7.6f, .45f, 1);
            for (float x : new float[]{-4.5f, 4.1f}) metal.cuboid(x, 7.1f, -.95f, .4f, 2, 1.7f);
        } else {
            float width = stage == 2 ? 5.4f : 4.75f, depth = stage == 2 ? 3.7f : 3.25f;
            leather.cuboid(-width, 17.8f, -depth, width * 2, .9f, .55f)
                    .cuboid(-width, 17.8f, depth - .55f, width * 2, .9f, .55f)
                    .cuboid(-width, 17.8f, -depth, .5f, .9f, depth * 2)
                    .cuboid(width - .5f, 17.8f, -depth, .5f, .9f, depth * 2)
                    .cuboid(-3.8f, 14, -depth - 2.1f, 7.6f, 8, 2.2f)
                    .cuboid(-4, 21.7f, -depth - 2.5f, 8, 1.1f, 2.7f)
                    .cuboid(-4, 13.6f, -depth - 2.5f, 8, 1.1f, 2.7f);
            metal.cuboid(-.75f, 17.4f, depth, 1.5f, 1.6f, .35f);
        }
        root.addChild("saddle", leather, ModelTransform.NONE);
        root.addChild("saddle_metal", metal, ModelTransform.NONE);
        var boxes = ModelPartBuilder.create().uv(0, 32);
        var locks = ModelPartBuilder.create().uv(16, 0);
        for (float x : new float[]{-7.2f, 4.05f}) {
            boxes.cuboid(x, 6.4f, -4.8f, 3.15f, 3.6f, 5.2f).cuboid(x - .12f, 9.6f, -4.92f, 3.4f, .65f, 5.45f);
            locks.cuboid(x < 0 ? x - .25f : x + 3.15f, 8.7f, -2.8f, .25f, 1.15f, 1.2f);
        }
        root.addChild("chests", boxes, ModelTransform.NONE);
        root.addChild("chest_metal", locks, ModelTransform.NONE);
        for (boolean left : new boolean[]{true, false}) {
            var claws = ModelPartBuilder.create().uv(32, 0);
            float center = left ? 4.565f : -4.565f;
            claws.cuboid(center - 1.15f, .7f, 4.2f, 2.3f, .55f, 1.3f);
            for (int i = 0; i < 3; i++) claws.cuboid(center - 1.08f + i * .75f, .11f, 5.15f, .66f, .65f, 1.65f);
            root.addChild(left ? "left_claws" : "right_claws", claws, ModelTransform.NONE);
        }
        var model = TexturedModelData.of(data, 64, 64).createModel();
        reins = model.getChild("reins"); reinsMetal = model.getChild("rings");
        blinkers = model.getChild("blinkers");
        saddle = model.getChild("saddle"); saddleMetal = model.getChild("saddle_metal");
        chest = model.getChild("chests"); chestMetal = model.getChild("chest_metal");
        leftClaws = model.getChild("left_claws"); rightClaws = model.getChild("right_claws");
    }

    private static void snoutBand(ModelPartBuilder straps, float half, float bottom, float top, float z) {
        float low = bottom - .39f, high = top + .04f;
        straps.cuboid(-half - .35f, high, z, 2 * (half + .35f), .35f, .6f)
                .cuboid(-half - .35f, low, z, 2 * (half + .35f), .35f, .6f)
                .cuboid(-half - .35f, low, z, .35f, high - low + .35f, .6f)
                .cuboid(half, low, z, .35f, high - low + .35f, .6f);
    }

    public void render(String bone, MatrixStack matrices, VertexConsumerProvider buffers, int light,
            boolean showReins, boolean showSaddle, boolean showChest, boolean showClaws) {
        render(bone, matrices, buffers, light, showReins, showSaddle, showChest, showClaws, false);
    }

    public void render(String bone, MatrixStack matrices, VertexConsumerProvider buffers, int light,
            boolean showReins, boolean showSaddle, boolean showChest, boolean showClaws, boolean vanillaSaddle) {
        render(bone, matrices, buffers, light, showReins, showSaddle, showChest, showClaws, vanillaSaddle, false);
    }

    public void render(String bone, MatrixStack matrices, VertexConsumerProvider buffers, int light,
            boolean showReins, boolean showSaddle, boolean showChest, boolean showClaws, boolean vanillaSaddle, boolean showBlinkers) {
        if (bone.equals("@left@_front_paw")) bone = "right_front_paw";
        if (!(bone.equals("bipedHead") && showReins || bone.equals("bipedBody") && (showSaddle || showChest)
                || showClaws && (bone.equals("left_front_paw") || bone.equals("right_front_paw")))) return;
        var buffer = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        if (bone.equals("bipedHead") && showReins) {
            reins.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV);
            reinsMetal.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV);
            if (showBlinkers) blinkers.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV);
        } else if (bone.equals("bipedBody")) {
            if (showSaddle) {
                if (vanillaSaddle) buffer = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(VANILLA_SADDLE_TEXTURE));
                saddle.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV);
                saddleMetal.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV);
                if (vanillaSaddle) buffer = buffers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
            }
            if (showChest) { chest.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV); chestMetal.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV); }
        } else if (showClaws && bone.equals("left_front_paw")) leftClaws.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV);
        else if (showClaws && bone.equals("right_front_paw")) rightClaws.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV);
    }
}
