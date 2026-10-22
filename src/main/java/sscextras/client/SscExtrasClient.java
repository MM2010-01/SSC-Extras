package sscextras.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.text.TranslatableTextContent;
import sscextras.SscExtrasGameRules;
import sscextras.collar.CollarItem;
import sscextras.collar.Collars;
import sscextras.effigy.FeralEffigy;

public final class SscExtrasClient implements ClientModInitializer {
    private static boolean removeBlackoutDuringTransformation = true;

    @Override public void onInitializeClient() {
        MentalPerception.register();
        var intelligenceBar = new IntelligenceBarRenderer();
        var quadBar = new QuadBarRenderer();
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(quadBar::render);
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(intelligenceBar::render);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.options.hudHidden) { intelligenceBar.reset(); quadBar.reset(); }
        });
        PetrificationClient.register();
        ClientPlayNetworking.registerGlobalReceiver(SscExtrasGameRules.TRANSFORMATION_BLACKOUT_SYNC, (client, handler, buf, sender) -> {
            boolean removeBlackout = buf.readBoolean();
            client.execute(() -> removeBlackoutDuringTransformation = removeBlackout);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> removeBlackoutDuringTransformation = true);
        ClientPlayNetworking.registerGlobalReceiver(SscExtrasGameRules.PERMANENT_PROGRESSION_SYNC, (client, handler, buf, sender) -> {
            boolean unlocked = buf.readBoolean();
            var exceptions = java.util.Set.copyOf(buf.readList(net.minecraft.network.PacketByteBuf::readIdentifier));
            client.execute(() -> SscExtrasGameRules.setClientPermanentProgression(unlocked, exceptions));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                SscExtrasGameRules.setClientPermanentProgression(true, java.util.Set.of()));
        GuardianClient.register();
        for (int stage = 0; stage < 4; stage++) {
            int index = stage;
            FormPlayerModels.register(sscextras.guardian.RuneboundGuardian.FORMS[stage].FormID, () -> new GuardianPlayerModel(index));
        }
        FormPlayerModels.register(sscextras.golem.DrakeGolem.FORM.FormID, DrakeGolemPlayerModel::new);
        DrakeEquipmentClient.register();
        DrakeCameraControl.register();
        FeralPerception.register();
        DrakeLeashRenderer.register();
        DrakeSoulRenderer.register();
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                sscextras.drake.DrakeStable.DRAKE, StableDrakeRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                sscextras.drake.DrakeStable.VISITOR, net.minecraft.client.render.entity.PillagerEntityRenderer::new);
        net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderUtils.register_MAS(
                sscextras.drake.EarthenDrake.id("earthen_drake"), EarthenDrakeAnimation::new);
        HandledScreens.register(FeralEffigy.SCREEN, EffigyScreen::new);
        HandledScreens.register(sscextras.drake.MountMerchants.SCREEN, MountMerchantScreen::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(sscextras.drake.MountMerchants.MERCHANT, MountMerchantRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(sscextras.drake.MountMerchants.BUYER, net.minecraft.client.render.entity.PillagerEntityRenderer::new);
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            if (!(stack.getItem() instanceof CollarItem)) return;
            var player = MinecraftClient.getInstance().player;
            if (player != null && Collars.isCursed(player)) {
                lines.removeIf(line -> line.getContent() instanceof TranslatableTextContent text
                        && text.getKey().equals("tooltip.ssc-extras.collar.infused"));
            }
        });
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                helper.register(new GuardianFeatureRenderer(playerRenderer));
                DrakeSoulRenderer.initialize(context);
                helper.register(new CollarFeatureRenderer(playerRenderer));
                helper.register(new CuffsFeatureRenderer(playerRenderer));
                helper.register(new TrainerFeatureRenderer(playerRenderer));
                helper.register(new DrakeGearFeatureRenderer(playerRenderer));
                helper.register(new DrakeShoesFeatureRenderer(playerRenderer));
            }
        });
    }

    public static boolean removeBlackoutDuringTransformation() { return removeBlackoutDuringTransformation; }
}
