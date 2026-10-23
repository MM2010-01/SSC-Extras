package sscextras.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import sscextras.golem.GolemStatues;

public final class GolemStatueControls {
    private GolemStatueControls() { }
    public static void register() {
        var key = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.ssc-extras.statue_mode", InputUtil.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_P, "category.ssc-extras"));
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            while (key.wasPressed()) {
                if (client.player == null || client.currentScreen != null) continue;
                var form = sscextras.golem.AbstractGolemForm.get(client.player);
                boolean sharedSocialKey = KeyBindingHelper.getBoundKeyOf(key).equals(KeyBindingHelper.getBoundKeyOf(client.options.socialInteractionsKey));
                if (client.player.isSpectator() || form == null || !form.supportsStatueControl() || !ClientPlayNetworking.canSend(GolemStatues.TOGGLE)) {
                    if (sharedSocialKey) {
                        var social = (sscextras.mixin.GolemStatueKeyAccessor)client.options.socialInteractionsKey;
                        if (social.sscExtras$queuedPresses() == 0) social.sscExtras$queuedPresses(1);
                    }
                    continue;
                }
                // Vanilla also defaults Social Interactions to P. Consume that matching press only for a golem toggle.
                if (sharedSocialKey)
                    while (client.options.socialInteractionsKey.wasPressed()) { }
                ClientPlayNetworking.send(GolemStatues.TOGGLE, PacketByteBufs.create());
            }
        });
    }
}
