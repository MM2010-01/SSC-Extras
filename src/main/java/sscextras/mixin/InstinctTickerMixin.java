package sscextras.mixin;

import sscextras.CreatureInstinct;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InstinctTicker.class, remap = false)
public abstract class InstinctTickerMixin {
    @Inject(method = "clearInstinct", at = @At("HEAD"))
    private static void sscExtras$clearTarget(PlayerEntity player, CallbackInfo ci) {
        CreatureInstinct.clearTarget(player);
    }
}
