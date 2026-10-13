package sscextras.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import sscextras.player.*;

public final class MentalPerception {
    private MentalPerception() { }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(sscextras.SscExtrasGameRules.INT_SYSTEM_SYNC, (client, handler, buffer, sender) -> {
            boolean enabled = buffer.readBoolean();
            client.execute(() -> sscextras.SscExtrasGameRules.setClientIntSystem(enabled));
        });
        ClientPlayNetworking.registerGlobalReceiver(PlayerAspects.RULES_PACKET, (client, handler, buffer, sender) -> {
            String rules = buffer.readString(262144);
            client.execute(() -> MentalRequirements.receive(rules));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MentalRequirements.resetClient(); sscextras.SscExtrasGameRules.setClientIntSystem(true);
        });
    }
    public static boolean unreadable(ItemStack stack) {
        var client = MinecraftClient.getInstance();
        return client.isOnThread() && client.player != null && !PlayerMind.check(client.player, MentalContext.item(MentalContext.Action.READ, stack)).allowed();
    }
    public static boolean unreadableScreen() {
        var client = MinecraftClient.getInstance();
        if (!client.isOnThread() || client.player == null || !(client.currentScreen instanceof HandledScreen<?> screen)
                || screen.getScreenHandler() == client.player.playerScreenHandler) return false;
        return !PlayerMind.permits(client.player, MentalRequirements.screen(screen.getScreenHandler()));
    }
    public static Text scramble(Text text) { return Text.literal(text.getString()).setStyle(text.getStyle().withObfuscated(true)); }
}
