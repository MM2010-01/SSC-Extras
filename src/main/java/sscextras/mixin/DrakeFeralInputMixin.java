package sscextras.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeFeralization;

@Mixin(ClientPlayerEntity.class)
public abstract class DrakeFeralInputMixin {
    @org.spongepowered.asm.mixin.Unique private int sscExtras$previousCaptureKeys;
    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void sscExtras$ignoreMovementKeys(CallbackInfo ci) {
        var player = (ClientPlayerEntity)(Object)this;
        var keys = net.minecraft.client.MinecraftClient.getInstance().options;
        int pressed = (keys.forwardKey.isPressed() ? 1 : 0) | (keys.backKey.isPressed() ? 2 : 0)
                | (keys.leftKey.isPressed() ? 4 : 0) | (keys.rightKey.isPressed() ? 8 : 0)
                | (keys.jumpKey.isPressed() ? 16 : 0) | (keys.sneakKey.isPressed() ? 32 : 0);
        if ((pressed & ~sscExtras$previousCaptureKeys) != 0 && sscextras.player.PlayerControl.active(player)
                && net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(sscextras.guardian.GuardianVillageMobs.STRUGGLE))
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(sscextras.guardian.GuardianVillageMobs.STRUGGLE,
                    net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
        sscExtras$previousCaptureKeys = pressed;
        if (!DrakeFeralization.controlled(player) && !sscextras.drake.DrakeSoulbinding.restrained(player)) return;
        var input = player.input;
        input.movementForward = input.movementSideways = 0;
        input.jumping = input.sneaking = input.pressingForward = input.pressingBack = input.pressingLeft = input.pressingRight = false;
        player.setSprinting(false); player.setSneaking(false);
    }
}
