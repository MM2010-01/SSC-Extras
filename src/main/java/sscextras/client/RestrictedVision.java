package sscextras.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import sscextras.statue.StatueCrates;

/** Stateless visual restrictions; the item/form components remain authoritative. */
public final class RestrictedVision {
    private RestrictedVision() { }

    public static boolean crated() {
        var player = MinecraftClient.getInstance().player;
        return player != null && player.isAlive() && StatueCrates.packed(player);
    }

    public static boolean blind(Camera camera) {
        return camera.isThirdPerson() && crated() || BlindingReinVision.blind(camera);
    }

    public static boolean firstPerson() {
        return MinecraftClient.getInstance().options.getPerspective().isFirstPerson()
                && (crated() || BlindingReinVision.active());
    }

    public static void renderOverlay(DrawContext context) {
        if (!firstPerson()) return;
        if (crated()) context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(), 0xff000000);
        else BlindingReinVision.renderBlinkers(context);
    }
}
