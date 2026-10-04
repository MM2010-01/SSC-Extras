package sscextras.mixin;

import sscextras.CreatureInstinct;
import net.minecraft.server.network.ServerPlayerEntity;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TransformManager.class, remap = false)
public abstract class TransformManagerMixin {
    @Inject(method = "getRandomOrBuffForm", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$chooseCreature(ServerPlayerEntity player,
                                                CallbackInfoReturnable<PlayerFormBase> cir) {
        if (!TransformManager.getPlayerTransformData(player)._isByCursedMoon) {
            PlayerFormBase target = CreatureInstinct.getTarget(player);
            if (target != null && target.getIndex() >= 0) {
                cir.setReturnValue(target);
            }
        }
    }
}
