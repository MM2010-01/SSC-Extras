package sscextras.mixin;

import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.RegPlayerFormComponent;
import net.onixary.shapeShifterCurseFabric.util.PlayerEventHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.guardian.RuneboundGuardian;

@Mixin(value = PlayerEventHandler.class, remap = false)
public abstract class GuardianFormRespawnMixin {
    @Inject(method = "copyFormAndAbility", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$coreDeathForm(ServerPlayerEntity oldPlayer, ServerPlayerEntity player, CallbackInfo ci) {
        if (!RuneboundGuardian.state(oldPlayer).coreDeath) return;
        RegPlayerFormComponent.PLAYER_FORM.get(player).setCurrentForm(RegPlayerForms.ORIGINAL_SHIFTER);
        // GuardianEquipment applies the form after the native respawn packet has installed the new player.
        ci.cancel();
    }

    @Inject(method = "copyTransformativeEffect", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$coreDeathCurse(ServerPlayerEntity oldPlayer, ServerPlayerEntity player, CallbackInfo ci) {
        if (RuneboundGuardian.state(oldPlayer).coreDeath) ci.cancel();
    }
}
