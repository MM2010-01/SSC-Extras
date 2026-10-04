package sscextras.mixin;

import sscextras.CreatureInstinct;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect;
import net.onixary.shapeShifterCurseFabric.status_effects.TStatusApplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TStatusApplier.class, remap = false)
public abstract class TransformativeStatusMixin {
    @Inject(method = "applyStatusByChance", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$replaceAttackStatus(float chance, PlayerEntity player,
                                                     BaseTransformativeStatusEffect effect, CallbackInfo ci) {
        // Cursed wolves call this from their successful-hit callback instead of ITMob's contact loop.
        CreatureInstinct.add(player, effect);
        ci.cancel();
    }
}
