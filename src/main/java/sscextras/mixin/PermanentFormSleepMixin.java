package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.drake.DrakeSoulbinding;

@Mixin(value = World.class, priority = 1100)
public abstract class PermanentFormSleepMixin {
    // SSC's tickEntity callback is renamed by Mixin. Match its named, intermediary, or Connector signature.
    @Dynamic("CursedMoonWorldMixin.tickEntity")
    @WrapOperation(method = {
            "(Ljava/util/function/Consumer;Lnet/minecraft/entity/Entity;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
            "(Ljava/util/function/Consumer;Lnet/minecraft/class_1297;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V",
            "(Ljava/util/function/Consumer;Lnet/minecraft/world/entity/Entity;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;wakeUp()V"))
    private void sscExtras$allowPermanentSleep(ServerPlayerEntity player, Operation<Void> original) {
        if (FormAbilityManager.getForm(player).getIndex() != 3 && !DrakeSoulbinding.bound(player)) original.call(player);
    }
}
