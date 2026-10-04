package sscextras.drake;

import net.minecraft.block.FenceGateBlock;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class DrakeStableNavigation extends MobNavigation {
    private DrakeStablePiece stable;
    private BlockPos guardHome;
    private int nextStableSearch;
    private final Map<BlockPos, Integer> openedGates = new HashMap<>();

    public DrakeStableNavigation(MobEntity entity, World world) { super(entity, world); }

    private boolean canOpenGates() {
        return entity instanceof net.minecraft.entity.mob.PillagerEntity || entity.getFirstPassenger() instanceof net.minecraft.entity.mob.PillagerEntity;
    }

    public DrakeStablePiece stable() {
        if (!(world instanceof ServerWorld server)) return null;
        if (entity.age >= nextStableSearch) {
            nextStableSearch = entity.age + 100;
            stable = DrakeCaptureGoal.findStable(server, guardHome == null ? entity.getBlockPos() : guardHome);
            if (stable != null && entity instanceof net.minecraft.entity.mob.PillagerEntity) home(stable);
        }
        return stable;
    }

    public void home(DrakeStablePiece home) { stable = home; guardHome = home.getBoundingBox().getCenter(); }
    public void writeHome(net.minecraft.nbt.NbtCompound nbt) {
        if (guardHome != null) nbt.putLong("SscExtrasStableHome", guardHome.asLong());
    }
    public void readHome(net.minecraft.nbt.NbtCompound nbt) {
        guardHome = nbt.contains("SscExtrasStableHome") ? BlockPos.fromLong(nbt.getLong("SscExtrasStableHome")) : null;
        stable = null; nextStableSearch = 0;
    }

    @Override protected PathNodeNavigator createPathNodeNavigator(int range) {
        nodeMaker = new LandPathNodeMaker() {
            @Override public PathNodeType getDefaultNodeType(BlockView world, int x, int y, int z) {
                var pos = new BlockPos(x, y, z);
                if (canOpenGates() && stable != null && stable.isGate(pos) && world.getBlockState(pos).getBlock() instanceof FenceGateBlock)
                    return PathNodeType.WALKABLE_DOOR;
                return super.getDefaultNodeType(world, x, y, z);
            }
        };
        nodeMaker.setCanEnterOpenDoors(true);
        return new PathNodeNavigator(nodeMaker, Math.max(range, 2048));
    }

    @Override protected Path findPathToAny(Set<BlockPos> positions, int range, boolean head, int distance, float followRange) {
        if (stable() != null) for (var pos : positions)
            followRange = Math.max(followRange, (float)Math.max(64, Math.min(DrakeCaptureGoal.RANGE * 3,
                    Math.sqrt(pos.getSquaredDistance(entity.getBlockPos())) * 2 + 16)));
        return super.findPathToAny(positions, range, head, distance, followRange);
    }

    public boolean reaches(BlockPos pos) {
        var path = findPathTo(pos, 1);
        return path != null && path.reachesTarget();
    }

    public void open(BlockPos gate) {
        boolean changed = false;
        for (var pos : new BlockPos[]{gate, gate.east()}) {
            var state = world.getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && !state.get(FenceGateBlock.OPEN)) {
                world.setBlockState(pos, state.with(FenceGateBlock.OPEN, true), 3);
                changed = true;
            }
        }
        if (changed) openedGates.put(gate, entity.age);
    }

    public boolean close(BlockPos gate) {
        var doorway = new Box(gate).stretch(1, 1, 0).expand(.25);
        if (!world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class, doorway,
                other -> other.isAlive() && !other.isSpectator()).isEmpty()) return false;
        for (var pos : new BlockPos[]{gate, gate.east()}) {
            var state = world.getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && state.get(FenceGateBlock.OPEN))
                world.setBlockState(pos, state.with(FenceGateBlock.OPEN, false), 3);
        }
        return true;
    }

    @Override public void tick() {
        if (canOpenGates() && currentPath != null && !currentPath.isFinished() && stable != null) {
            for (int i = currentPath.getCurrentNodeIndex(); i < Math.min(currentPath.getLength(), currentPath.getCurrentNodeIndex() + 3); i++) {
                var pos = currentPath.getNodePos(i);
                if (stable.isGate(pos) && entity.squaredDistanceTo(Vec3d.ofCenter(pos)) < 9)
                    open(stable.gateAt(Vec3d.ofCenter(pos)));
            }
        }
        super.tick();
        if (openedGates.isEmpty()) return;
        openedGates.entrySet().removeIf(entry -> {
            var gate = entry.getKey();
            if (entity.age - entry.getValue() < 20) return false;
            var capture = entity instanceof DrakeCaptureGoal.Captor captor ? captor.sscExtras$captureGoal() : null;
            if (capture != null && capture.holdsOpen(gate)) return false;
            var battle = entity instanceof DrakeBattleGoal.Rider rider ? rider.sscExtras$battleGoal() : null;
            if (battle == null && DrakeBattleGoal.assigned(entity)) battle = DrakeBattleGoal.of(((DrakeRiding.State)entity).sscExtras$battleRider());
            if (battle != null && battle.holdsOpen(gate)) return false;
            for (var player : world.getPlayers()) {
                if (DrakeLeashing.holder(player) == entity
                        && (player.getZ() - gate.getZ() - .5) * (entity.getZ() - gate.getZ() - .5) < 0) return false;
            }
            return close(gate);
        });
    }
}
