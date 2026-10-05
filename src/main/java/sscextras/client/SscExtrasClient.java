package sscextras.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.text.TranslatableTextContent;
import sscextras.collar.CollarItem;
import sscextras.collar.Collars;
import sscextras.effigy.FeralEffigy;

public final class SscExtrasClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        DrakeEquipmentClient.register();
        DrakeLeashRenderer.register();
        DrakeSoulRenderer.register();
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                sscextras.drake.DrakeStable.DRAKE, StableDrakeRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                sscextras.drake.DrakeStable.VISITOR, net.minecraft.client.render.entity.PillagerEntityRenderer::new);
        net.onixary.shapeShifterCurseFabric.render.form_render.FormRenderUtils.register_MAS(
                sscextras.drake.EarthenDrake.id("earthen_drake"), EarthenDrakeAnimation::new);
        HandledScreens.register(FeralEffigy.SCREEN, EffigyScreen::new);
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
                DrakeSoulRenderer.initialize(context);
                helper.register(new CollarFeatureRenderer(playerRenderer));
                helper.register(new CuffsFeatureRenderer(playerRenderer));
                helper.register(new DrakeGearFeatureRenderer(playerRenderer));
            }
        });
    }
}
