package sscextras.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeFeralization;

@Mixin(ClientPlayerEntity.class)
public abstract class DrakeFeralInputMixin {
    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void sscExtras$ignoreMovementKeys(CallbackInfo ci) {
        var player = (ClientPlayerEntity)(Object)this;
        if (!DrakeFeralization.controlled(player) && !sscextras.drake.DrakeSoulbinding.restrained(player)) return;
        var input = player.input;
        input.movementForward = input.movementSideways = 0;
        input.jumping = input.sneaking = input.pressingForward = input.pressingBack = input.pressingLeft = input.pressingRight = false;
        player.setSprinting(false); player.setSneaking(false);
    }
}
