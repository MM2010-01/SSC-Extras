package sscextras.mixin;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.PatrolEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeStableGuards;

@Mixin(PatrolEntity.class)
public abstract class DrakePillagerSpawnMixin {
    @Inject(method = "canSpawn", at = @At("RETURN"), cancellable = true)
    private static void sscExtras$stablePopulation(EntityType<? extends PatrolEntity> type, WorldAccess world,
            SpawnReason reason, BlockPos pos, Random random, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && type == EntityType.PILLAGER && world instanceof ServerWorld server
                && (reason == SpawnReason.NATURAL || reason == SpawnReason.PATROL)
                && !DrakeStableGuards.canSpawn(server, pos)) cir.setReturnValue(false);
    }
}
