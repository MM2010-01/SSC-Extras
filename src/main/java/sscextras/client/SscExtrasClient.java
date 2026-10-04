package sscextras.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import sscextras.effigy.FeralEffigy;

public final class SscExtrasClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        HandledScreens.register(FeralEffigy.SCREEN, EffigyScreen::new);
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof PlayerEntityRenderer playerRenderer) {
                helper.register(new CollarFeatureRenderer(playerRenderer));
            }
        });
    }
}
