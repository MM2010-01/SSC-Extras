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
    private boolean managedGuard, legacyGuard;
    private int nextStableSearch;
    private final Map<BlockPos, Integer> openedGates = new HashMap<>();

    public DrakeStableNavigation(MobEntity entity, World world) { super(entity, world); }

    private boolean canOpenGates() {
        return entity instanceof net.minecraft.entity.mob.PillagerEntity
                || entity.getFirstPassenger() instanceof net.minecraft.entity.mob.PillagerEntity;
    }

    public DrakeStablePiece stable() {
        if (!(world instanceof ServerWorld server)) return null;
        if (entity instanceof DrakeVisitorEntity visitor) return stable = visitor.stable();
        if (entity.age >= nextStableSearch) {
            nextStableSearch = entity.age + 100;
            stable = DrakeCaptureGoal.findStable(server, guardHome == null ? entity.getBlockPos() : guardHome);
            if (stable != null && entity instanceof net.minecraft.entity.mob.PillagerEntity) home(stable);
        }
        return stable;
    }

    public void home(DrakeStablePiece home) { stable = home; guardHome = home.getBoundingBox().getCenter(); }
    public boolean managedGuard() { return managedGuard; }
    public boolean legacyGuard() { return legacyGuard; }
    public void managedGuard(boolean managed) { managedGuard = managed; legacyGuard = false; }
    public void writeHome(net.minecraft.nbt.NbtCompound nbt) {
        if (guardHome != null) nbt.putLong("SscExtrasStableHome", guardHome.asLong());
        nbt.putBoolean("SscExtrasManagedGuard", managedGuard);
        nbt.putBoolean("SscExtrasLegacyGuard", legacyGuard);
    }
    public void readHome(net.minecraft.nbt.NbtCompound nbt) {
        guardHome = nbt.contains("SscExtrasStableHome") ? BlockPos.fromLong(nbt.getLong("SscExtrasStableHome")) : null;
        managedGuard = nbt.getBoolean("SscExtrasManagedGuard");
        legacyGuard = nbt.getBoolean("SscExtrasLegacyGuard") || guardHome != null && !nbt.contains("SscExtrasManagedGuard");
        stable = null; nextStableSearch = 0;
    }

    @Override protected PathNodeNavigator createPathNodeNavigator(int range) {
        nodeMaker = playerNodes(null);
        nodeMaker.setCanEnterOpenDoors(true);
        return new PathNodeNavigator(nodeMaker, Math.max(range, 2048));
    }

    private LandPathNodeMaker playerNodes(net.minecraft.entity.player.PlayerEntity origin) {
        return new LandPathNodeMaker() {
            private net.minecraft.entity.player.PlayerEntity player() {
                return origin != null ? origin : entity.getVehicle() instanceof net.minecraft.entity.player.PlayerEntity player
                        && DrakeRiding.canCarryPillager(player) ? player : null;
            }
            @Override public void init(net.minecraft.world.chunk.ChunkCache cache, MobEntity mob) {
                super.init(cache, mob);
                var player = player();
                if (player != null) {
                    entityBlockXSize = entityBlockZSize = net.minecraft.util.math.MathHelper.floor(player.getWidth() + 1);
                    entityBlockYSize = net.minecraft.util.math.MathHelper.floor(player.getHeight() + 1);
                }
            }
            @Override public net.minecraft.entity.ai.pathing.PathNode getStart() {
                var player = player();
                if (player == null) return super.getStart();
                var pos = BlockPos.ofFloored(player.getX(), player.getY() + (player.isOnGround() ? .5 : 0), player.getZ());
                if (canSwim() && player.isTouchingWater()) {
                    while (pos.getY() < world.getTopY() - 1 && cachedWorld.getFluidState(pos.up()).isIn(net.minecraft.registry.tag.FluidTags.WATER)) pos = pos.up();
                } else if (!player.isOnGround()) {
                    while (pos.getY() > world.getBottomY() && (cachedWorld.getBlockState(pos).isAir()
                            || cachedWorld.getBlockState(pos).canPathfindThrough(cachedWorld, pos, net.minecraft.entity.ai.pathing.NavigationType.LAND))) pos = pos.down();
                    pos = pos.up();
                }
                // Match vanilla's fallback when a wide body straddles a blocked starting node.
                if (!canPathThrough(pos)) {
                    var bounds = player.getBoundingBox();
                    for (double x : new double[]{bounds.minX, bounds.maxX}) for (double z : new double[]{bounds.minZ, bounds.maxZ}) {
                        var corner = BlockPos.ofFloored(x, pos.getY(), z);
                        if (canPathThrough(corner)) return getStart(corner);
                    }
                }
                return getStart(pos);
            }
            @Override public PathNodeType getDefaultNodeType(BlockView world, int x, int y, int z) {
                var pos = new BlockPos(x, y, z);
                if (canOpenGates() && stable != null && stable.isGate(pos) && world.getBlockState(pos).getBlock() instanceof FenceGateBlock)
                    return PathNodeType.WALKABLE_DOOR;
                return super.getDefaultNodeType(world, x, y, z);
            }
        };
    }

    public Path leadPath(net.minecraft.entity.player.PlayerEntity player) {
        var nodes = playerNodes(player);
        nodes.setCanEnterOpenDoors(true);
        nodes.setCanSwim(canSwim());
        var pos = player.getBlockPos();
        var cache = new net.minecraft.world.chunk.ChunkCache(world, pos.add(-16, -16, -16), pos.add(16, 16, 16));
        return new PathNodeNavigator(nodes, 1024).findPathToAny(cache, entity, Set.of(entity.getRootVehicle().getBlockPos()), 24, 0, 1);
    }

    @Override protected Vec3d getPos() {
        return entity.getVehicle() instanceof net.minecraft.entity.player.PlayerEntity player ? player.getPos() : super.getPos();
    }

    @Override public Path findPathTo(BlockPos target, int distance) {
        if (canSwim() && world.getFluidState(target).isIn(net.minecraft.registry.tag.FluidTags.WATER)) {
            while (target.getY() < world.getTopY() - 1 && world.getFluidState(target.up()).isIn(net.minecraft.registry.tag.FluidTags.WATER)) target = target.up();
        }
        return super.findPathTo(target, distance);
    }

    @Override protected void continueFollowingPath() {
        if (entity.getVehicle() instanceof net.minecraft.entity.player.PlayerEntity player) {
            DrakeRiding.advancePath(currentPath, player);
            checkTimeouts(player.getPos());
        } else super.continueFollowingPath();
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
        var path = currentPath;
        if (canOpenGates() && path != null && !path.isFinished() && stable != null) {
            // Opening a gate can synchronously replace or clear the active path.
            for (int i = path.getCurrentNodeIndex(); currentPath == path && i < Math.min(path.getLength(), path.getCurrentNodeIndex() + 3); i++) {
                var pos = path.getNodePos(i);
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
