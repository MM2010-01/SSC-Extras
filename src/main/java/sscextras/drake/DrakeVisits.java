package sscextras.drake;

import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

public final class DrakeVisits {
    private DrakeVisits() { }

    public static void tick(ServerWorld world, DrakeStablePiece stable) {
        if (world.getTime() % 1200 != 0 || !world.isDay() || world.random.nextInt(8) != 0) return;
        spawn(world, stable);
    }

    public static DrakeVisitorEntity spawn(ServerWorld world, DrakeStablePiece stable) {
        var area = Box.from(stable.getBoundingBox()).expand(96);
        if (!world.getEntitiesByClass(DrakeVisitorEntity.class, area, visitor -> visitor.isAlive()).isEmpty()
                || !world.getEntitiesByClass(PillagerEntity.class, area, guard -> guard.hasActiveRaid() || DrakeFaction.fighting(guard)).isEmpty()
                || world.getEntitiesByClass(StableDrakeEntity.class, area, drake -> drake.belongsTo(stable)).isEmpty()
                    && world.getPlayers().stream().noneMatch(player -> EarthenDrake.stage(player) >= 0 && DrakeOutpostOwnership.owns(player, stable))) return null;
        var visitor = DrakeStable.VISITOR.create(world);
        if (visitor == null) return null;
        for (int attempt = 0; attempt < 16; attempt++) {
            var column = new BlockPos(stable.getBoundingBox().getMinX() - 8 + world.random.nextInt(stable.getBoundingBox().getBlockCountX() + 16),
                    0, stable.getBoundingBox().getMinZ() - 16 - world.random.nextInt(16));
            if (!world.isChunkLoaded(column)) continue;
            var pos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
            if (!world.getFluidState(pos.down()).isEmpty() || !world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) continue;
            visitor.setPosition(Vec3d.ofBottomCenter(pos));
            visitor.setOnGround(true);
            if (!world.isSpaceEmpty(visitor) || !world.getOtherEntities(visitor, visitor.getBoundingBox()).isEmpty()) continue;
            visitor.visit(stable, pos);
            var path = visitor.getNavigation().findPathTo(stable.gate(0).north(2), 1);
            if (path == null || !path.reachesTarget()) continue;
            visitor.initialize(world, world.getLocalDifficulty(pos), SpawnReason.EVENT, null, null);
            world.spawnEntity(visitor);
            visitor.getNavigation().startMovingAlong(path, .65);
            return visitor;
        }
        return null;
    }
}
