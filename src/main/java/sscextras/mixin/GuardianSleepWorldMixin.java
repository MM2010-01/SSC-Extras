package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.guardian.GuardianSleep;

@Mixin(ServerWorld.class)
public abstract class GuardianSleepWorldMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;setTimeOfDay(J)V"))
    private void sscExtras$skipToNight(ServerWorld world, long target, Operation<Void> original) {
        long next = GuardianSleep.skipTarget(world, target); GuardianSleep.skipped(world, next); original.call(world, next);
    }
}
