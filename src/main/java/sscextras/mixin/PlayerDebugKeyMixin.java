package sscextras.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.player.PlayerDebug;

@Mixin(Keyboard.class)
public abstract class PlayerDebugKeyMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void sscExtras$debug(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        int chord = GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT;
        if (window != client.getWindow().getHandle() || client.player == null || client.currentScreen != null
                || (modifiers & chord) != chord || action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT
                || !ClientPlayNetworking.canSend(PlayerDebug.ADJUST)) return;
        int change = switch (key) {
            case GLFW.GLFW_KEY_UP -> 0;
            case GLFW.GLFW_KEY_DOWN -> 1;
            case GLFW.GLFW_KEY_LEFT -> 2;
            case GLFW.GLFW_KEY_RIGHT -> 3;
            default -> -1;
        };
        if (change < 0) return;
        var packet = PacketByteBufs.create(); packet.writeByte(change);
        ClientPlayNetworking.send(PlayerDebug.ADJUST, packet); ci.cancel();
    }
}
