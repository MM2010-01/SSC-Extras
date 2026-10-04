package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.WitchEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.status_effects.RegTStatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.CreatureInstinct;

@Mixin(value = Entity.class, priority = 900)
public abstract class WitchInstinctTeamMixin {
    @Unique private static final Identifier SSC_EXTRAS_FAMILIAR = new Identifier("ssc_addon", "witch_familiar");

    @Inject(method = "isTeammate", at = @At("HEAD"), cancellable = true)
    private void sscExtras$allowFamiliarInstinctAttacks(Entity other, CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (other instanceof PlayerEntity player && (self instanceof WitchEntity
                || Registries.ENTITY_TYPE.getId(self.getType()).equals(SSC_EXTRAS_FAMILIAR))
                && CreatureInstinct.canGain(player, RegTStatusEffect.TO_FAMILIAR_FOX_0_EFFECT)) {
            cir.setReturnValue(self.getScoreboardTeam() != null && self.getScoreboardTeam().isEqual(other.getScoreboardTeam()));
        }
    }
}
