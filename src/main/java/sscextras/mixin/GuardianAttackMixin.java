package sscextras.mixin;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.GuardianCombat;

@Mixin(MinecraftClient.class)
public abstract class GuardianAttackMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void sscExtras$overdriveAttack(CallbackInfoReturnable<Boolean> cir) {
        var client = (MinecraftClient)(Object)this;
        var player = client.player;
        if (player == null || player.isSpectator() || !GuardianCombat.overdriveClaws(player)
                || client.crosshairTarget == null || client.crosshairTarget.getType() == HitResult.Type.BLOCK) return;
        if (!GuardianCombat.locked(player) && !player.isUsingItem()) {
            ClientPlayNetworking.send(GuardianCombat.CLAW_SWIPE, PacketByteBufs.create());
            player.resetLastAttackedTicks();
        }
        cir.setReturnValue(false);
    }
}
