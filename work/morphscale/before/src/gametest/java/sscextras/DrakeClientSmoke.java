package sscextras;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import sscextras.drake.EarthenDrake;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderUtils;
import mod.azure.azurelib.util.RenderUtils;

import java.nio.file.Files;

public final class DrakeClientSmoke implements ClientModInitializer {
    private int scene = -1, wait, runFrame;
    private boolean hero;
    private float tailLeft, tailRight;
    private float runningTailLeft, runningTailRight, runningTailRoll;
    public static boolean cape;
    public static final boolean GROUND = Boolean.getBoolean("ssc-extras.drake-ground");
    public static int groundView;
    private static final boolean REFERENCES = Boolean.getBoolean("ssc-extras.drake-references");
    private static final boolean SEAMS = Boolean.getBoolean("ssc-extras.drake-seams");
    private static final String[] NAMES = REFERENCES ? new String[]{"SSC wolf stage 0", "SSC wolf stage 1", "SSC wolf stage 2",
            "SSC bat stage 2", "SSC axolotl stage 2", "SSC axolotl permanent"} : new String[]{"Stage 0", "Stage 1", "Stage 2 - standing", "Stage 2 - hungry", "Permanent", "Permanent - mouth item",
            "Permanent - walking", "Permanent - running", "Permanent - crouching", "Stage 2 - mouth item, looking", "Permanent - collar, looking", "Permanent - first-person paws", "Permanent - cape", "Permanent - cape, crouching",
            "Stage 2 - running", "Stage 2 - custom colors", "Permanent - custom colors", "Stage 2 - walking",
            "Stage 2 - sneaking", "Stage 2 - sneak walking", "Stage 2 - released sneak",
            "Stage 2 - cape, standing", "Stage 2 - cape, sneaking", "Stage 2 - cape, sneak walking", "Stage 2 - cape, running"};

    private int stage() { return scene < 2 ? scene : scene < 4 || scene == 9 || scene == 14 || scene == 15 || scene >= 17 ? 2 : 3; }
    private boolean moving() { return scene == 6 || scene == 7 || scene == 14 || scene == 17 || scene == 19 || scene == 23 || scene == 24; }
    private boolean sprinting() { return scene == 7 || scene == 14 || scene == 24; }
    private boolean sneaking() { return scene == 8 || scene == 13 || scene == 18 || scene == 19 || scene == 22 || scene == 23; }

    @Override public void onInitializeClient() {
        if (Boolean.getBoolean("ssc-extras.sentient-preview")) { new SentientPreviewClient().register(); return; }
        if (!Boolean.getBoolean("ssc-extras.drake-client-smoke") || Boolean.getBoolean("ssc-extras.equipment-render") || Boolean.getBoolean("ssc-extras.drake-ritual")) return;
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> GLFW.glfwHideWindow(client.getWindow().getHandle()));
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            client.options.forwardKey.setPressed(moving());
            client.options.sprintKey.setPressed(sprinting());
            client.options.sneakKey.setPressed(sneaking());
            if (moving()) client.player.setPosition(.5 + .03 * (client.player.age % 2), 90, .5);
            if (GROUND) {
                client.options.hudHidden = true;
                client.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_BACK);
                client.player.setYaw(180); client.player.prevYaw = 180;
                client.player.bodyYaw = client.player.prevBodyYaw = 180;
                client.player.headYaw = client.player.prevHeadYaw = 180;
                client.player.setPitch(0); client.player.prevPitch = 0;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            client.getToastManager().clear();
            if (scene < 0) {
                if (++wait >= 60) {
                    checkColdModel(client);
                    next(client);
                }
                return;
            }
            if (scene >= NAMES.length) return;
            if (++wait < 80) return;
            if (!GROUND && !(client.currentScreen instanceof Preview)) { client.setScreen(new Preview()); wait = 0; return; }
            if (!REFERENCES && (EarthenDrake.stage(client.player) != stage() || EarthenDrake.onAllFours(client.player) != (stage() == 3 || scene == 3 || scene == 15 || sprinting() || sneaking()))) {
                throw new IllegalStateException("Client did not receive drake stage/posture for scene " + scene);
            }
            if (sprinting() && !client.player.isSprinting() || sneaking() && !client.player.isSneaking()) {
                throw new IllegalStateException("Movement input did not reach player for scene " + scene);
            }
            try {
                var dir = client.runDirectory.toPath().resolve("screenshots");
                Files.createDirectories(dir);
                try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                    image.writeTo(dir.resolve(GROUND ? "earthen_drake_ground_" + scene + "_" + groundView + ".png"
                            : REFERENCES ? "ssc_reference_" + scene + ".png" : "earthen_drake_" + (SEAMS ? "seams_" : "") + (hero ? "hero" : scene == 14 ? scene + "_" + runFrame : scene) + ".png"));
                }
                System.out.println("SSC_DRAKE_RENDER_SCENE " + scene);
                if (GROUND) {
                    if (++groundView < 4) { wait = 55; return; }
                    groundView = 0;
                    next(client);
                    return;
                }
                if (!REFERENCES && (scene == 3 || scene == 18 || scene == 22)) {
                    var body = ((PlayerEntityRenderer)client.getEntityRenderDispatcher().getRenderer(client.player)).getModel().body;
                    if (Math.abs(body.pivotY) > .01f) throw new IllegalStateException("All-fours idle inherited vanilla crouch offset: " + body.pivotY);
                    System.out.println("SSC_DRAKE_IDLE_ALIGNMENT_PASS " + scene);
                }
                if (!REFERENCES && (scene == 0 || scene == 4)) checkCamera(client);
                if (!REFERENCES && scene == 4) {
                    if (tailLeft > -.01f || tailRight < .01f) throw new IllegalStateException("Tail did not sway both ways: " + tailLeft + ", " + tailRight);
                    System.out.println("SSC_DRAKE_TAIL_PASS yaw=" + tailLeft + ".." + tailRight);
                }
                if (!REFERENCES && scene == 14) {
                    if (runningTailLeft > -.01f || runningTailRight < .01f || runningTailRoll > .002f)
                        throw new IllegalStateException("Running tail twisted instead of swaying: "
                                + runningTailLeft + ".." + runningTailRight + ", roll=" + runningTailRoll);
                    System.out.println("SSC_DRAKE_RUNNING_TAIL_PASS lateral=" + runningTailLeft + ".." + runningTailRight
                            + ", roll=" + runningTailRoll);
                }
                if (!REFERENCES && !SEAMS && scene == 4 && !hero) { hero = true; wait = 0; return; }
                hero = false;
                if (scene == 14 && runFrame++ < 7) { wait = 77; return; }
                next(client);
            } catch (Exception error) {
                throw new RuntimeException("Drake render probe failed", error);
            }
        });
    }

    private void checkColdModel(MinecraftClient client) {
        var resource = client.getResourceManager().getResource(EarthenDrake.id(
                "ssc_form_model/origins.origin.ssc-extras.form_earthen_drake_2.json")).orElseThrow();
        try (var reader = resource.getReader()) {
            var model = new net.onixary.shapeShifterCurseFabric.render.form_render.FormModel(
                    com.google.gson.JsonParser.parseReader(reader).getAsJsonObject());
            model.setPlayer(client.player, false);
            var renderer = (PlayerEntityRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);
            if (model.getCachedGeoBone("bipedLeftLeg") != null) throw new IllegalStateException("Expected unbaked stage 2 model");
            model.AnimationSystem.beforeRender(null, model, renderer, client.player, 0, 0, 1, 0, 0, 0);
            model.AnimationSystem.processAnimation(null, model, renderer, client.player, 0, 0, 1, 0, 0, 0);
            model.getAnimationProcessor().setActiveModel(model.getBakedModel(model.ModelResource));
            if (model.getCachedGeoBone("bipedLeftLeg") == null) throw new IllegalStateException("Stage 2 bones did not load");
            model.AnimationSystem.processAnimation(null, model, renderer, client.player, 0, 0, 1, 0, 0, 0);
            System.out.println("SSC_DRAKE_COLD_MODEL_PASS");
        } catch (java.io.IOException error) {
            throw new RuntimeException("Could not load stage 2 model for cold-render check", error);
        }
    }

    private void next(MinecraftClient client) {
        ++scene;
        if (GROUND) while (scene < NAMES.length && scene != 2 && scene != 3 && scene != 4
                && scene != 14 && scene != 18 && scene != 19 && scene != 20) ++scene;
        cape = scene == 12 || scene == 13 || scene >= 21;
        if (scene >= NAMES.length) {
            System.out.println("SSC_DRAKE_RENDER_PASS");
            client.setScreen(null);
            client.scheduleStop();
            return;
        }
        var packet = PacketByteBufs.create();
        packet.writeInt(scene);
        ClientPlayNetworking.send(EarthenDrake.id("preview_scene"), packet);
        client.setScreen(GROUND ? null : new Preview());
        wait = 0;
    }

    private void checkCamera(MinecraftClient client) {
        var player = client.player;
        var camera = new Camera();
        for (int yaw : new int[]{0, 90, 180, -90}) for (int pitch : new int[]{0, 30, -30}) {
            player.bodyYaw = player.prevBodyYaw = yaw;
            player.headYaw = player.prevHeadYaw = yaw + 20;
            player.setYaw(yaw + 20); player.prevYaw = yaw + 20;
            player.setPitch(pitch); player.prevPitch = pitch;
            camera.update(client.world, player, false, false, 1);
            for (int i=0; i<32; i++) camera.updateEyeHeight();
            camera.update(client.world, player, false, false, 1);
            var ray = player.getCameraPosVec(1);
            if (camera.getPos().distanceTo(ray) > .002) throw new IllegalStateException("Camera/interaction origin mismatch: " + camera.getPos() + " vs " + ray);
            var bodyEyes = player.getPos().add(0, player.getStandingEyeHeight(), 0);
            double distance = new Vec3d(ray.x-bodyEyes.x, 0, ray.z-bodyEyes.z).length();
            if (scene == 4 ? distance < .79 || distance > .95 : distance > .001)
                throw new IllegalStateException("Unexpected head camera distance: " + distance);
            camera.update(client.world, player, true, false, 1);
            if (camera.getPos().subtract(ray).crossProduct(player.getRotationVec(1)).length() > .003)
                throw new IllegalStateException("Third-person camera does not orbit the head");
        }
        if (scene == 4) {
            player.bodyYaw = player.prevBodyYaw = player.headYaw = player.prevHeadYaw = 0;
            player.setYaw(0); player.prevYaw = 0; player.setPitch(0); player.prevPitch = 0;
            var wall = BlockPos.ofFloored(player.getX(), player.getEyeY(), player.getZ()+.9);
            var original = client.world.getBlockState(wall);
            try {
                client.world.setBlockState(wall, Blocks.STONE.getDefaultState());
                camera.update(client.world, player, false, false, 1);
                if (camera.getPos().z >= wall.getZ() || player.getCameraPosVec(1).z >= wall.getZ())
                    throw new IllegalStateException("Head camera or interaction ray entered the wall");
            } finally { client.world.setBlockState(wall, original); }
        }
        System.out.println("SSC_DRAKE_CAMERA_PASS stage=" + stage());
    }

    private final class Preview extends Screen {
        Preview() { super(Text.literal("Earthen Drake preview")); }
        @Override public boolean shouldPause() { return false; }
        @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            if (scene >= NAMES.length) return;
            context.fill(0,0,width,height,0xff17221e);
            context.fill(0,height-65,width,height,0xff26372e);
            context.drawText(textRenderer, REFERENCES ? "SSC MODEL REFERENCES" : "EARTHEN DRAKE", 24, 22, 0xffc7c291, false);
            context.drawText(textRenderer, NAMES[scene], 24, 40, 0xffeee8d2, false);
            context.drawText(textRenderer, "Minecraft 1.20.1 / SSC native renderer", 24, height-36, 0xffb9c2aa, false);
            var player = client.player;
            player.bodyYaw = player.prevBodyYaw = player.headYaw = player.prevHeadYaw = 180;
            player.setYaw(180);
            player.setPitch(0);
            if (scene == 9 || scene == 10) {
                player.headYaw = player.prevHeadYaw = 205;
                player.setYaw(205);
                player.setPitch(-15);
            }
            int size = Math.min(height / 4, width / 9);
            if (SEAMS && stage() < 2) size = size * 3 / 2;
            if (scene == 11) {
                var renderer = (PlayerEntityRenderer)client.getEntityRenderDispatcher().getRenderer(player);
                var vertices = client.getBufferBuilders().getEntityVertexConsumers();
                for (int hand = 0; hand < 2; hand++) {
                    context.getMatrices().push();
                    context.getMatrices().translate(width * (hand == 0 ? .3 : .7), height*.45, 1000);
                    context.getMatrices().scale(size*2, size*2, size*2);
                    context.getMatrices().multiply(new Quaternionf().rotationY((float)Math.toRadians(35)));
                    if (hand == 0) renderer.renderRightArm(context.getMatrices(), vertices, 15728880, player);
                    else renderer.renderLeftArm(context.getMatrices(), vertices, 15728880, player);
                    vertices.draw();
                    context.getMatrices().pop();
                }
                return;
            }
            for (int view = 0; view < (hero ? 1 : 3); view++) {
                int x = width * (view * 2 + 1) / 6;
                if (!hero) context.drawCenteredTextWithShadow(textRenderer, (SEAMS ? new String[]{"Side", "Rear three-quarter", "Back"} : new String[]{"Front", "Three-quarter", "Side"})[view], x, 70, 0xffb9c2aa);
                if (!REFERENCES && stage() == 2) context.fill(x-size/2, height-90, x+size/2, height-89, 0xff536358);
                var rotation = new Quaternionf().rotationZ((float)Math.PI).rotateY((float)Math.toRadians(hero ? 65 : SEAMS ? view == 0 ? 90 : view == 1 ? 132 : 180 : view == 0 ? 0 : view == 1 ? 48 : 90));
                context.getMatrices().push();
                // Large models extend behind the inventory renderer's usual 50-pixel depth.
                context.getMatrices().translate(0, 0, 1000);
                InventoryScreen.drawEntity(context, hero ? width*13/20 : x, height-90,
                        hero ? size*9/5 : size, rotation, null, player);
                context.getMatrices().pop();
            }
            if (!REFERENCES && scene == 4) {
                for (var renderer : FormRenderUtils.getPlayerAllFormRenderer(player)) {
                    var tail = renderer.realModel.getCachedGeoBone("tail_0");
                    if (tail != null) {
                        tailLeft = Math.min(tailLeft, tail.getRotY());
                        tailRight = Math.max(tailRight, tail.getRotY());
                    }
                }
            }
            if (!REFERENCES && scene == 14) {
                for (var renderer : FormRenderUtils.getPlayerAllFormRenderer(player)) {
                    var body = renderer.realModel.getCachedGeoBone("bipedBody");
                    var tail = renderer.realModel.getCachedGeoBone("tail_0");
                    if (body == null || tail == null) continue;
                    var matrices = new MatrixStack();
                    RenderUtils.rotateMatrixAroundBone(matrices, body);
                    RenderUtils.rotateMatrixAroundBone(matrices, tail);
                    var matrix = matrices.peek().getPositionMatrix();
                    var forward = matrix.transformDirection(new Vector3f(0, 0, -1));
                    var side = matrix.transformDirection(new Vector3f(1, 0, 0));
                    runningTailLeft = Math.min(runningTailLeft, forward.x);
                    runningTailRight = Math.max(runningTailRight, forward.x);
                    runningTailRoll = Math.max(runningTailRoll, Math.abs(side.y));
                }
            }
        }
    }
}
