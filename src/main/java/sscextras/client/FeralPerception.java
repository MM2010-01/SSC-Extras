package sscextras.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.OrderedText;
import sscextras.drake.*;

public final class FeralPerception {
    private FeralPerception() { }
    public static boolean active() {
        var client = MinecraftClient.getInstance();
        if (!client.isOnThread()) return false;
        var player = client.player;
        return player != null && DrakeFeralization.restricted(player);
    }
    public static OrderedText scramble(OrderedText text) {
        if (!active()) return text;
        var client = MinecraftClient.getInstance();
        var screen = client.currentScreen;
        if (screen != null && !(screen instanceof net.minecraft.client.gui.screen.ChatScreen)
                && !(screen instanceof net.minecraft.client.gui.screen.ingame.BookScreen)
                && !(screen instanceof net.minecraft.client.gui.screen.ingame.BookEditScreen)) return text;
        return FeralText.scramble(text, ((DrakeOutpostOwnership.Display)client.player).sscExtras$mountName());
    }
}
