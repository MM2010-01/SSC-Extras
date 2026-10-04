package sscextras.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import sscextras.drake.DrakeEquipment;

public final class BlindingReinVision {
    private BlindingReinVision() { }

    public static boolean worn() {
        var client = MinecraftClient.getInstance();
        return client.player != null && client.player.isAlive() && client.getCameraEntity() == client.player
                && !DrakeEquipment.equipped(client.player, DrakeEquipment.BLINDING_REIN).isEmpty();
    }

    public static boolean blind(Camera camera) {
        return camera.isThirdPerson() && worn();
    }

    public static void renderBlinkers(DrawContext context) {
        var client = MinecraftClient.getInstance();
        if (!client.options.getPerspective().isFirstPerson() || !worn()) return;
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        int opening = Math.min(width * 44 / 100, height);
        int edge = (width - opening) / 2;
        for (int y = 0; y < height; y += 2) {
            float distance = Math.abs(y * 2f / height - 1);
            int inset = edge + (int)(opening * .12f * distance * distance);
            context.fill(0, y, inset, Math.min(y + 2, height), 0xff080605);
            context.fill(width - inset, y, width, Math.min(y + 2, height), 0xff080605);
            context.fill(inset - 2, y, inset, Math.min(y + 2, height), 0xff281b16);
            context.fill(width - inset, y, width - inset + 2, Math.min(y + 2, height), 0xff281b16);
        }
    }
}
