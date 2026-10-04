package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.status_effects.RegTStatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.CreatureInstinct;

@Pseudo
@Mixin(targets = "net.jackcooper.shapeShifterCurseAddon.entity.AxolotlShifterEntity", remap = false)
public abstract class AddonAxolotlInstinctMixin extends PathAwareEntity {
    protected AddonAxolotlInstinctMixin(EntityType<? extends PathAwareEntity> type, World world) { super(type, world); }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sscExtras$seekInstinctTargets(CallbackInfo ci) {
        targetSelector.add(3, new ActiveTargetGoal<>(this, PlayerEntity.class, true,
                target -> target instanceof PlayerEntity player
                        && CreatureInstinct.canGain(player, RegTStatusEffect.TO_AXOLOTL_0_EFFECT)));
    }

    // Pseudo targets need explicit names for Yarn, Fabric intermediary, and Connector's SRG runtime.
    @Redirect(method = {"tryAttack", "method_6121", "m_7327_"}, at = @At(value = "INVOKE", remap = true, target =
            "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean sscExtras$harmlessContact(Entity target, DamageSource source, float amount) {
        if (target instanceof PlayerEntity player && CreatureInstinct.matchesForm(player, RegTStatusEffect.TO_AXOLOTL_0_EFFECT)) {
            CreatureInstinct.add(player, RegTStatusEffect.TO_AXOLOTL_0_EFFECT);
            return true;
        }
        return target.damage(source, amount);
    }

    @Redirect(method = "useWaterBurst", at = @At(value = "INVOKE", remap = true, target =
            "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean sscExtras$harmlessWaterBurst(Entity target, DamageSource source, float amount) {
        return sscExtras$harmlessContact(target, source, amount);
    }
}
