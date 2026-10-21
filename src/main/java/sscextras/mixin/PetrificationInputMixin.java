package sscextras.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.petrification.Petrification;

@Mixin(ClientPlayerEntity.class)
public abstract class PetrificationInputMixin {
    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void petrificationInput(CallbackInfo ci) {
        var player = (ClientPlayerEntity)(Object)this;
        if (!Petrification.frozen(player)) return;
        var input = player.input;
        input.movementForward = input.movementSideways = 0;
        input.jumping = input.sneaking = input.pressingForward = input.pressingBack = input.pressingLeft = input.pressingRight = false;
    }
}
