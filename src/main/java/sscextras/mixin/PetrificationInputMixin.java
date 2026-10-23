package sscextras.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.petrification.Petrification;

@Mixin(ClientPlayerEntity.class)
public abstract class PetrificationInputMixin {
    @Unique private int sscExtras$previousMovement;
    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void petrificationInput(CallbackInfo ci) {
        var player = (ClientPlayerEntity)(Object)this;
        var input = player.input;
        int movement = (input.pressingForward ? 1 : 0) | (input.pressingBack ? 2 : 0)
                | (input.pressingLeft ? 4 : 0) | (input.pressingRight ? 8 : 0)
                | (input.jumping ? 16 : 0) | (input.sneaking ? 32 : 0);
        boolean fresh = (movement & ~sscExtras$previousMovement) != 0;
        sscExtras$previousMovement = movement;
        if (fresh && Petrification.stone(player)
                && net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(Petrification.MOVEMENT_BREAK))
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(Petrification.MOVEMENT_BREAK,
                    net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create());
        if (!sscextras.statue.StatueBodies.locked(player)) return;
        input.movementForward = input.movementSideways = 0;
        input.jumping = input.sneaking = input.pressingForward = input.pressingBack = input.pressingLeft = input.pressingRight = false;
    }
}
