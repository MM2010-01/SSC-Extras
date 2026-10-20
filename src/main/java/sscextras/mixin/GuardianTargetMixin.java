package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import sscextras.guardian.GuardianVillages;

@Mixin(MobEntity.class)
public abstract class GuardianTargetMixin {
    @ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
    private LivingEntity sscExtras$drawVillagerAggro(LivingEntity target) {
        return GuardianVillages.redirectTarget((MobEntity)(Object)this, target);
    }
}
