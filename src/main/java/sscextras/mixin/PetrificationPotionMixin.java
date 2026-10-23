package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.PotionItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.petrification.Petrification;

@Mixin(PotionItem.class)
public abstract class PetrificationPotionMixin {
    @WrapOperation(method = "finishUsing", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/LivingEntity;addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;)Z"))
    private boolean petrificationDrink(LivingEntity entity, StatusEffectInstance effect, Operation<Boolean> original) {
        return effect.getEffectType() == Petrification.EFFECT
                ? Petrification.state(entity).drink(() -> original.call(entity, effect)) : original.call(entity, effect);
    }
}
