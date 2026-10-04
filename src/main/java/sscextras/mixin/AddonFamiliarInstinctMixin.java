package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.status_effects.RegTStatusEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import sscextras.CreatureInstinct;

@Pseudo
@Mixin(targets = "net.jackcooper.shapeShifterCurseAddon.entity.WitchFamiliarEntity", remap = false)
public abstract class AddonFamiliarInstinctMixin extends HostileEntity {
    protected AddonFamiliarInstinctMixin(EntityType<? extends HostileEntity> type, World world) { super(type, world); }

    @Override public boolean tryAttack(Entity target) {
        if (target instanceof PlayerEntity player && CreatureInstinct.matchesForm(player, RegTStatusEffect.TO_FAMILIAR_FOX_0_EFFECT)) {
            CreatureInstinct.add(player, RegTStatusEffect.TO_FAMILIAR_FOX_0_EFFECT);
            return true;
        }
        return super.tryAttack(target);
    }

    @Redirect(method = "useFireRing", at = @At(value = "INVOKE", remap = true, target =
            "Lnet/minecraft/entity/LivingEntity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"))
    private boolean sscExtras$harmlessFireRing(LivingEntity target, DamageSource source, float amount) {
        if (target instanceof PlayerEntity player && CreatureInstinct.matchesForm(player, RegTStatusEffect.TO_FAMILIAR_FOX_0_EFFECT)) {
            CreatureInstinct.add(player, RegTStatusEffect.TO_FAMILIAR_FOX_0_EFFECT);
            return true;
        }
        return target.damage(source, amount);
    }

    @Redirect(method = "useFireRing", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/entity/LivingEntity;setOnFireFor(I)V"))
    private void sscExtras$avoidBurningInstinctTarget(LivingEntity target, int seconds) {
        if (!(target instanceof PlayerEntity player)
                || !CreatureInstinct.matchesForm(player, RegTStatusEffect.TO_FAMILIAR_FOX_0_EFFECT)) target.setOnFireFor(seconds);
    }
}
