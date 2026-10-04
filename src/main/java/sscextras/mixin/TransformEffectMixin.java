package sscextras.mixin;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.effect.PlayerTransformEffectManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = PlayerTransformEffectManager.class, remap = false)
public abstract class TransformEffectMixin {
    @Redirect(method = "applyStartTransformEffect", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/network/ServerPlayerEntity;addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;)Z",
            remap = true))
    private static boolean sscExtras$skipVisionEffects(ServerPlayerEntity player, StatusEffectInstance effect) {
        return effect.getEffectType() != StatusEffects.BLINDNESS
                && effect.getEffectType() != StatusEffects.DARKNESS
                && player.addStatusEffect(effect);
    }
}
