package sscextras.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeShoes;
import sscextras.drake.DrakeEquipment;
import sscextras.drake.DrakeSoulbinding;

@Mixin(PlayerEntity.class)
public abstract class DrakeShoesPlayerMixin {
    @ModifyExpressionValue(method = "attack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;getAttributeValue(Lnet/minecraft/entity/attribute/EntityAttribute;)D", ordinal = 0))
    private double sscExtras$ironClaws(double damage) {
        var player = (PlayerEntity)(Object)this;
        return DrakeShoes.hands(player) && player.getInventory().getMainHandStack().isEmpty() ? damage + 5 : damage;
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void sscExtras$noMouthWeapon(Entity entity, CallbackInfo ci) {
        var player = (PlayerEntity)(Object)this;
        if (DrakeSoulbinding.restrained(player) || DrakeShoes.restricted(player) && !player.getInventory().getMainHandStack().isEmpty()) ci.cancel();
    }

    @ModifyArg(method = "increaseTravelMotionStats", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;addExhaustion(F)V", ordinal = 3), index = 0)
    private float sscExtras$efficientSprint(float exhaustion) {
        var player = (PlayerEntity)(Object)this;
        return DrakeShoes.feet(player) || !DrakeEquipment.footClaws(player).isEmpty() ? exhaustion * .7f : exhaustion;
    }

    @ModifyArg(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;addExhaustion(F)V"), index = 0)
    private float sscExtras$efficientSprintJump(float exhaustion) {
        var player = (PlayerEntity)(Object)this;
        return player.isSprinting() && (DrakeShoes.feet(player) || !DrakeEquipment.footClaws(player).isEmpty())
                ? exhaustion * .7f : exhaustion;
    }
}
