package sscextras.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.*;

@Mixin(ServerPlayerEntity.class)
public abstract class GuardianBedMixin {
    @Inject(method = "trySleep", at = @At("HEAD"), cancellable = true)
    private void sscExtras$onlyDayForStone(BlockPos pos, CallbackInfoReturnable<Either<PlayerEntity.SleepFailureReason, Unit>> cir) {
        var player = (ServerPlayerEntity)(Object)this;
        if (RuneboundGuardian.stage(player) >= 2 && !GuardianSleep.canSleepNow(player))
            cir.setReturnValue(Either.left(PlayerEntity.SleepFailureReason.NOT_POSSIBLE_NOW));
    }
    @Inject(method = "trySleep", at = @At("RETURN"))
    private void sscExtras$rememberSleepPhase(BlockPos pos, CallbackInfoReturnable<Either<PlayerEntity.SleepFailureReason, Unit>> cir) {
        var player = (ServerPlayerEntity)(Object)this;
        if (RuneboundGuardian.stage(player) >= 1 && cir.getReturnValue().right().isPresent()) {
            var state = RuneboundGuardian.state(player); state.daySleep = player.getWorld().isDay(); state.sync();
        }
    }
}
