package sscextras.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.OrderedText;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import sscextras.drake.*;

public final class FeralPerception {
    public static final int READABLE_TICKS = 100, FADE_TICKS = 200;
    private static ClientPlayerEntity observedPlayer;
    private static boolean wasInRitual;
    private static int releasedAt = -1;

    private FeralPerception() { }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var player = client.player;
            if (observedPlayer != player) {
                observedPlayer = player; wasInRitual = false; releasedAt = -1;
            }
            if (player == null) return;
            boolean inRitual = DrakeSoulbinding.role(player) != 0;
            if (wasInRitual && !inRitual && DrakeFeralization.feral(player)) releasedAt = player.age;
            if (!DrakeFeralization.feral(player)) releasedAt = -1;
            wasInRitual = inRitual;
        });
    }

    private static float strength(ClientPlayerEntity player) {
        if (DrakeSoulbinding.role(player) != 0) return 0;
        if (releasedAt < 0 || observedPlayer != player) return 1;
        return net.minecraft.util.math.MathHelper.clamp((player.age - releasedAt - READABLE_TICKS) / (float)FADE_TICKS, 0, 1);
    }
    public static boolean active() {
        var client = MinecraftClient.getInstance();
        if (!client.isOnThread()) return false;
        var player = client.player;
        return player != null && DrakeFeralization.mindRestricted(player);
    }
    public static OrderedText scramble(OrderedText text) {
        if (!active()) return text;
        var client = MinecraftClient.getInstance();
        var screen = client.currentScreen;
        if (screen != null && !(screen instanceof net.minecraft.client.gui.screen.ChatScreen)
                && !(screen instanceof net.minecraft.client.gui.screen.ingame.BookScreen)
                && !(screen instanceof net.minecraft.client.gui.screen.ingame.BookEditScreen)) return text;
        return FeralText.scramble(text, ((DrakeOutpostOwnership.Display)client.player).sscExtras$mountName(), strength(client.player));
    }
}
