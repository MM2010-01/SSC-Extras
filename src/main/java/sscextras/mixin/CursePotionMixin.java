package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect;
import net.onixary.shapeShifterCurseFabric.status_effects.transformative_effects.TransformativeStatusPotion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.CreatureInstinct;
import sscextras.SscExtras;

@Mixin(TransformativeStatusPotion.class)
public abstract class CursePotionMixin {
    @Shadow(remap = false) public BaseTransformativeStatusEffect TransformativeStatusEffect;

    @Inject(method = "applyInstantEffect", at = @At("TAIL"))
    private void sscExtras$addPotionInstinct(Entity source, Entity attacker, LivingEntity target,
                                           int amplifier, double proximity, CallbackInfo ci) {
        if (target instanceof PlayerEntity player) {
            CreatureInstinct.add(player, TransformativeStatusEffect, SscExtras.instinctPerPotion());
        }
    }
}
