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
        return active() ? FeralText.scramble(text, ((DrakeOutpostOwnership.Display)MinecraftClient.getInstance().player).sscExtras$mountName()) : text;
    }
}
