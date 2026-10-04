package sscextras.drake;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import java.util.HashSet;

public final class DrakeStableGuards {
    public static final int MINIMUM = 4;
    private DrakeStableGuards() { }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 200 != 0 || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)
                    || world.getDifficulty() == Difficulty.PEACEFUL) return;
            var checked = new HashSet<BlockBox>();
            for (var player : world.getPlayers()) {
                if (player.isSpectator()) continue;
                var stable = DrakeCaptureGoal.findStable(world, player.getBlockPos());
                if (stable != null && checked.add(stable.getBoundingBox())) replenish(world, stable);
            }
        });
    }

    public static void replenish(ServerWorld world, DrakeStablePiece stable) {
        if (!world.isChunkLoaded(stable.gate(0)) || world.getDifficulty() == Difficulty.PEACEFUL
                || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) return;
        int count = 0, nearby = 0, outer = 0;
        for (var guard : world.getEntitiesByClass(PillagerEntity.class, Box.from(stable.getBoundingBox()).expand(DrakeCaptureGoal.RANGE),
                entity -> entity.isAlive())) {
            if (!DrakeCaptureGoal.near(stable, guard.getPos())) continue;
            outer++;
            if (DrakeCaptureGoal.near(stable.getBoundingBox(), guard.getPos(), DrakeRoaming.RANGE)) nearby++;
            if (guard.hasActiveRaid()) continue;
            var navigation = (DrakeStableNavigation)guard.getNavigation();
            var home = navigation.stable();
            if (home != null && home.getBoundingBox().equals(stable.getBoundingBox())) {
                guard.setPersistent(); count++;
            }
        }
        for (int i = count; i < MINIMUM; i++) {
            if (outer >= 10 || nearby >= 5) return;
            var guard = EntityType.PILLAGER.create(world);
            if (guard == null) return;
            boolean placed = false;
            for (int attempt = 0; attempt < 24; attempt++) {
                int x = stable.getBoundingBox().getMinX() - 12 + world.random.nextInt(46);
                int z = stable.getBoundingBox().getMinZ() - 6 - world.random.nextInt(36);
                var column = new BlockPos(x, 0, z);
                if (!world.isChunkLoaded(column)) continue;
                var pos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
                if (!world.getFluidState(pos.down()).isEmpty() || !world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) continue;
                guard.setPosition(Vec3d.ofBottomCenter(pos));
                if (!world.isSpaceEmpty(guard) || !world.getOtherEntities(guard, guard.getBoundingBox()).isEmpty()) continue;
                placed = true; break;
            }
            if (!placed) return;
            guard.initialize(world, world.getLocalDifficulty(guard.getBlockPos()), SpawnReason.STRUCTURE, null, null);
            guard.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
            guard.setPersistent();
            ((DrakeStableNavigation)guard.getNavigation()).home(stable);
            world.spawnEntity(guard);
            outer++;
            if (DrakeCaptureGoal.near(stable.getBoundingBox(), guard.getPos(), DrakeRoaming.RANGE)) nearby++;
        }
    }
}
