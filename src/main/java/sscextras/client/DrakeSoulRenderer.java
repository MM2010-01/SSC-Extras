package sscextras.client;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.player_form.skin.RegPlayerSkinComponent;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormAnimatable;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormModel;
import net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderUtils;
import sscextras.drake.DrakeSoulbinding;
import sscextras.drake.EarthenDrake;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;

public final class DrakeSoulRenderer {
    private static final HashMap<UUID, Soul> SOULS = new HashMap<>();
    private static PlayerEntityRenderer renderer, slimRenderer;
    private static EntityRendererFactory.Context renderContext;

    private DrakeSoulRenderer() { }

    public static void initialize(EntityRendererFactory.Context context) {
        if (renderContext == context) return;
        renderContext = context;
        renderer = new PlayerEntityRenderer(context, false);
        slimRenderer = new PlayerEntityRenderer(context, true);
        SOULS.clear();
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) SOULS.clear();
            else SOULS.values().removeIf(soul -> soul.owner.getWorld() != client.world || soul.owner.isRemoved()
                    || DrakeSoulbinding.soulTicks(soul.owner) == 0 || DrakeSoulbinding.soulTicks(soul.owner) > DrakeSoulbinding.SOUL_RETURN_TICKS);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (renderer == null || context.consumers() == null) return;
            for (var player : context.world().getPlayers()) {
                int ticks = DrakeSoulbinding.soulTicks(player);
                if (ticks == 0 || ticks > DrakeSoulbinding.SOUL_RETURN_TICKS || !player.isAlive()
                        || player.squaredDistanceTo(context.camera().getPos()) > 4096) continue;
                var soul = SOULS.computeIfAbsent(player.getUuid(), id -> new Soul(player));
                float time = soul.time(ticks, context.tickDelta());
                float rise = smooth(time / 60), returning = smooth((time - DrakeSoulbinding.SOUL_TRANSFORM_TICKS) / 60);
                float alpha = .38f * Math.min(1, time / 18) * (1 - returning);
                var position = player.getLerpedPos(context.tickDelta()).subtract(context.camera().getPos());
                var matrices = context.matrixStack();
                matrices.push();
                matrices.translate(position.x, position.y + (2.2 + .045 * MathHelper.sin(time * .07f)) * rise * (1 - returning), position.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 - MathHelper.lerpAngleDegrees(context.tickDelta(), player.prevBodyYaw, player.bodyYaw)));
                float shrink = 1 - .65f * returning;
                matrices.scale(shrink, shrink, shrink);
                float changing = MathHelper.clamp((time - 60) / 50, 0, 4);
                int step = Math.min(4, (int)changing);
                float blend = smooth((changing - step) * (50f / 30));
                render(soul, step - 1, alpha * (1 - blend), context.tickDelta(), matrices, context.consumers());
                if (step < 4 && blend > 0) render(soul, step, alpha * blend, context.tickDelta(), matrices, context.consumers());
                matrices.pop();
            }
        });
    }

    private static float smooth(float value) {
        value = MathHelper.clamp(value, 0, 1);
        return value * value * (3 - 2 * value);
    }

    private static void render(Soul soul, int stage, float alpha, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers) {
        if (alpha < .002f) return;
        var ghost = soul.ghost;
        ghost.age = soul.owner.age;
        ghost.setPosition(soul.owner.getPos());
        var form = stage < 0 ? RegPlayerForms.ORIGINAL_SHIFTER : EarthenDrake.FORMS[stage];
        RegPlayerFormComponent.PLAYER_FORM.get(ghost).setCurrentForm(form);
        var playerRenderer = stage < 0 && soul.owner.getModel().equals("slim") ? slimRenderer : renderer;
        var body = playerRenderer.getModel();
        body.setVisible(true); body.child = false; body.sneaking = false; body.riding = false; body.handSwingProgress = 0;
        body.setAngles(ghost, 0, 0, ghost.age + tickDelta, 0, 0);
        if (stage == 2) EarthenDrakeAnimation.poseAllFours(body, 0, 0, ghost.age + tickDelta, 0, 0);
        var formRenderer = stage < 0 ? null : FormRenderUtils.getFormRenderer(form.getFormOriginLayerID(), form.getFormOriginID());
        FormModel model = formRenderer == null ? null : (FormModel)formRenderer.getGeoModel();
        if (model != null) hideParts(body, model);
        float scale = stage == 3 ? 1.5f : stage == 2 ? 2.2f / 1.8f : stage == 1 ? 2f / 1.8f : 1;
        matrices.push();
        matrices.scale(-.9375f * scale, -.9375f * scale, .9375f * scale);
        matrices.translate(0, -1.501, 0);
        var skinRenderer = (net.minecraft.client.render.entity.EntityRenderer<AbstractClientPlayerEntity>)playerRenderer;
        body.render(matrices, buffers.getBuffer(RenderLayer.getEntityTranslucent(skinRenderer.getTexture(ghost))),
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, .62f, .84f, 1, alpha);
        if (model != null) {
            var animatable = (FormAnimatable)formRenderer.getAnimatable();
            var previousPlayer = animatable.e;
            var previousModelPlayer = model.entity;
            formRenderer.setPlayer(ghost, false);
            try {
                var baked = model.getBakedModel(model.getModelResource(animatable));
                model.getAnimationProcessor().setActiveModel(baked);
                matrices.push();
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));
                matrices.translate(0, -1.51, 0);
                matrices.translate(-.5, -.5, -.5);
                model.AnimationSystem.beforeRender(formRenderer, model, renderer, ghost, 0, 0, tickDelta, ghost.age + tickDelta, 0, 0);
                model.AnimationSystem.processAnimation(formRenderer, model, renderer, ghost, 0, 0, tickDelta, ghost.age + tickDelta, 0, 0);
                var layer = RenderLayer.getEntityTranslucent(model.getTextureResource(animatable));
                formRenderer.reRender(baked, matrices, buffers, animatable, layer, buffers.getBuffer(layer), tickDelta,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, .62f, .84f, 1, alpha);
                model.AnimationSystem.afterRender(formRenderer, model, renderer, ghost, 0, 0, tickDelta, ghost.age + tickDelta, 0, 0);
                matrices.pop();
            } finally {
                animatable.e = previousPlayer;
                model.entity = previousModelPlayer;
                FormModel.SlimMap.remove(ghost);
            }
        }
        matrices.pop();
    }

    private static void hideParts(PlayerEntityModel<?> body, FormModel model) {
        body.hat.visible = !model.Hidden_Hat; body.head.visible = !model.Hidden_Head;
        body.body.visible = !model.Hidden_Body; body.jacket.visible = !model.Hidden_Jacket;
        body.leftArm.visible = !model.Hidden_LeftArm; body.rightArm.visible = !model.Hidden_RightArm;
        body.leftSleeve.visible = !model.Hidden_LeftSleeve; body.rightSleeve.visible = !model.Hidden_RightSleeve;
        body.leftLeg.visible = !model.Hidden_LeftLeg; body.rightLeg.visible = !model.Hidden_RightLeg;
        body.leftPants.visible = !model.Hidden_LeftPants; body.rightPants.visible = !model.Hidden_RightPants;
    }

    private static final class Soul {
        final AbstractClientPlayerEntity owner;
        final OtherClientPlayerEntity ghost;
        int ticks;
        long changedAt;
        Soul(AbstractClientPlayerEntity owner) {
            this.owner = owner;
            UUID id = UUID.nameUUIDFromBytes(("ssc-extras:soul:" + owner.getUuid()).getBytes(StandardCharsets.UTF_8));
            ghost = new OtherClientPlayerEntity(MinecraftClient.getInstance().world, new GameProfile(id, owner.getGameProfile().getName())) {
                @Override public net.minecraft.util.Identifier getSkinTexture() { return owner.getSkinTexture(); }
                @Override public String getModel() { return owner.getModel(); }
            };
            var skin = new NbtCompound();
            RegPlayerSkinComponent.SKIN_SETTINGS.get(owner).writeToNbt(skin);
            RegPlayerSkinComponent.SKIN_SETTINGS.get(ghost).readFromNbt(skin);
        }
        float time(int value, float delta) {
            long now = owner.getWorld().getTime();
            if (value != ticks) { ticks = value; changedAt = now; }
            return Math.min(value, Math.max(0, value - 20) + (now - changedAt) + delta);
        }
    }

}
