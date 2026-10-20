package sscextras.mixin;

import com.bawnorton.mixinsquared.TargetHandler;
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
    // Select SSC's own merged callback, independently of other mods' same-signature handlers.
    @Dynamic("CursedMoonWorldMixin.tickEntity")
    @TargetHandler(mixin = "net.onixary.shapeShifterCurseFabric.mixin.CursedMoonWorldMixin", name = "tickEntity")
    @WrapOperation(method = "@MixinSquared:Handler", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerPlayerEntity;wakeUp()V", remap = true),
            require = 1, allow = 1)
    private void sscExtras$allowPermanentSleep(ServerPlayerEntity player, Operation<Void> original) {
        if (FormAbilityManager.getForm(player).getIndex() != 3 && !DrakeSoulbinding.bound(player)
                && sscextras.guardian.RuneboundGuardian.stage(player) < 1) original.call(player);
    }
}
