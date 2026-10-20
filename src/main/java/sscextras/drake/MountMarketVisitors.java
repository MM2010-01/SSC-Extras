package sscextras.drake;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

final class MountMarketVisitors {
    static final int INTERVAL = 200, DEADLINE = 1200;

    private MountMarketVisitors() { }

    static boolean spawn(MobEntity visitor, Vec3d counter) {
        var world = (ServerWorld)visitor.getWorld();
        if (!world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) return false;
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            var column = BlockPos.ofFloored(counter.add(Math.cos(angle) * 32, 0, Math.sin(angle) * 32));
            if (!world.isChunkLoaded(column) || !world.shouldTick(column)) continue;
            var pos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
            if (world.getBlockState(pos.down()).getCollisionShape(world, pos.down()).isEmpty()
                    || !world.getFluidState(pos.down()).isEmpty() || !world.getFluidState(pos).isEmpty()) continue;
            visitor.refreshPositionAndAngles(pos, 0, 0);
            visitor.setOnGround(true);
            if (!world.isSpaceEmpty(visitor) || !world.getOtherEntities(visitor, visitor.getBoundingBox()).isEmpty()) continue;
            if (world.spawnEntity(visitor)) return true;
        }
        return false;
    }
}
