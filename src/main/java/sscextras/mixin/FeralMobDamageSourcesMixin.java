package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import sscextras.drake.FeralMobBrain;

@Mixin(DamageSources.class)
public abstract class FeralMobDamageSourcesMixin {
    @ModifyVariable(method = "create(Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/entity/Entity;)Lnet/minecraft/entity/damage/DamageSource;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Entity sscExtras$feralMeleeAttacker(Entity attacker) { return sscExtras$player(attacker); }

    @ModifyVariable(method = "create(Lnet/minecraft/registry/RegistryKey;Lnet/minecraft/entity/Entity;Lnet/minecraft/entity/Entity;)Lnet/minecraft/entity/damage/DamageSource;",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private Entity sscExtras$feralProjectileAttacker(Entity attacker) { return sscExtras$player(attacker); }

    private static Entity sscExtras$player(Entity attacker) {
        return attacker instanceof FeralMobBrain.Bridge brain && brain.sscExtras$feralPlayer() != null
                ? brain.sscExtras$feralPlayer() : attacker;
    }
}
