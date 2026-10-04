package sscextras.mixin;

import sscextras.CreatureInstinct;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.form_giving_custom_entity.ITMob;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = ITMob.class, remap = false)
public interface TransformativeMobMixin {
    @Inject(method = "TMob_TryAttack", at = @At("RETURN"))
    default void sscExtras$onAttack(MobEntity mob, Entity target,
                                   CallbackInfoReturnable<Optional<Boolean>> cir) {
        if (!(target instanceof PlayerEntity player) || mob.getWorld().isClient) {
            return;
        }
        ITMob cursed = (ITMob) this;
        if (cir.getReturnValue().orElse(false)) {
            CreatureInstinct.add(player, cursed.getStatusEffect());
        } else if (!RegPlayerForms.ORIGINAL_SHIFTER.equals(FormAbilityManager.getForm(player))
                && !cursed.IsInCooldown() && CreatureInstinct.add(player, cursed.getStatusEffect())) {
            // SSC's transformed-player contact does no damage; retain its normal curse cooldown.
            cursed.ApplyCooldown();
        }
    }

    @Redirect(method = "TMob_Tick", at = @At(value = "INVOKE", target =
            "Lnet/onixary/shapeShifterCurseFabric/status_effects/TStatusApplier;applyStatusByChance"))
    default void sscExtras$replaceContactStatus(float chance, PlayerEntity player,
                                               BaseTransformativeStatusEffect effect) {
        // The attack result above owns gain; the following status roll must not count it twice.
    }
}
