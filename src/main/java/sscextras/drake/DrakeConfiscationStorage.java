package sscextras.drake;

import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** One saved confiscation depot per stable. Native chests own delivered items, this record owns only transit. */
final class DrakeConfiscationStorage {
    final BlockBox stable;
    final UUID reservation = UUID.randomUUID();
    final List<ItemStack> cargo = new ArrayList<>();
    final List<BlockPos> chests = new ArrayList<>();
    final List<BlockBox> rooms = new ArrayList<>();
    BlockBox building;

    DrakeConfiscationStorage(BlockBox stable) { this.stable = stable; }

    boolean resolveBuilding(ServerWorld world) {
        if (building != null) return true;
        var center = stable.getCenter();
        if (!world.isChunkLoaded(center)) return false;
        var registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        var outpost = registry.get(new Identifier("minecraft", "pillager_outpost"));
        var mansion = registry.get(new Identifier("minecraft", "mansion"));
        for (var start : world.getStructureAccessor().getStructureStarts(new ChunkPos(center),
                type -> type == outpost || type == mansion)) {
            if (!belongs(start)) continue;
            var pieces = start.getChildren().stream().filter(piece -> !(piece instanceof DrakeStablePiece)).toList();
            if (pieces.isEmpty()) return false;
            // The outpost jigsaw root is its tower; the other pieces can be tents/cages outside it.
            building = start.getStructure() == outpost ? pieces.get(0).getBoundingBox()
                    : BlockBox.encompass(pieces.stream().map(piece -> piece.getBoundingBox()).toList()).orElse(null);
            if (start.getStructure() == outpost) rooms.add(building);
            else pieces.forEach(piece -> rooms.add(piece.getBoundingBox()));
            DrakeOutpostOwnership.get(world.getServer()).markDirty();
            return building != null;
        }
        return false;
    }

    private boolean belongs(StructureStart start) {
        return start.getChildren().stream().anyMatch(piece -> piece instanceof DrakeStablePiece
                && piece.getBoundingBox().equals(stable));
    }

    record Destination(BlockPos chest, BlockPos partner, BlockPos stand, boolean create) { }

    Destination destination(PillagerEntity guard) {
        var world = (ServerWorld)guard.getWorld();
        if (!resolveBuilding(world)) return null;
        for (var pos : chests) {
            if (!world.isChunkLoaded(pos)) continue;
            var inventory = inventory(world, pos);
            if (inventory == null || cargo.stream().noneMatch(stack -> space(inventory, stack))) continue;
            var stand = approach(guard, pos);
            if (stand != null) return new Destination(pos, null, stand, false);
        }
        var origin = chests.isEmpty() ? building.getCenter() : chests.get(chests.size() - 1);
        var candidates = new ArrayList<BlockPos>();
        // Search only the known building and loaded terrain, on real floors, preserving every existing block.
        for (var mutable : BlockPos.iterate(building.getMinX(), building.getMinY(), building.getMinZ(),
                building.getMaxX(), building.getMaxY(), building.getMaxZ())) {
            if (free(world, mutable) && free(world, mutable.east())) candidates.add(mutable.toImmutable());
        }
        candidates.sort(Comparator.comparingDouble(pos -> chests.isEmpty()
                ? (pos.getY() - building.getMinY()) * 10000.0 + pos.getSquaredDistance(origin)
                : pos.getSquaredDistance(origin)));
        for (var pos : candidates) {
            var stand = approach(guard, pos);
            if (stand != null) return new Destination(pos, pos.east(), stand, true);
        }
        return null;
    }

    private boolean free(ServerWorld world, BlockPos pos) {
        return building.contains(pos) && world.isChunkLoaded(pos) && world.isInBuildLimit(pos)
                && world.isAir(pos) && world.isAir(pos.up())
                && world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)
                && (rooms.isEmpty() || rooms.stream().anyMatch(room -> room.contains(pos)))
                && world.getOtherEntities(null, new Box(pos), entity -> entity.isAlive() && !entity.isSpectator()).isEmpty();
    }

    private BlockPos approach(PillagerEntity guard, BlockPos chest) {
        var world = (ServerWorld)guard.getWorld();
        for (var direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST}) {
            var pos = chest.offset(direction);
            if (!world.isChunkLoaded(pos) || !world.isAir(pos) || !world.isAir(pos.up())
                    || !world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)) continue;
            var path = guard.getNavigation().findPathTo(pos, 0);
            if (path != null && path.reachesTarget()) return pos;
        }
        return null;
    }

    boolean deliver(PillagerEntity guard, Destination destination) {
        var world = (ServerWorld)guard.getWorld();
        if (guard.squaredDistanceTo(Vec3d.ofBottomCenter(destination.stand())) > 4) return false;
        if (destination.create()) {
            if (!free(world, destination.chest()) || !free(world, destination.partner())) return false;
            var chest = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH);
            // Install both halves before enabling their connection so neighbor updates cannot split the pair.
            world.setBlockState(destination.chest(), chest, 3);
            world.setBlockState(destination.partner(), chest.with(ChestBlock.CHEST_TYPE, ChestType.RIGHT), 3);
            world.setBlockState(destination.chest(), chest.with(ChestBlock.CHEST_TYPE, ChestType.LEFT), 3);
            chests.add(destination.chest()); chests.add(destination.partner());
        }
        var inventory = inventory(world, destination.chest());
        if (inventory == null) return false;
        for (var stack : cargo) insert(inventory, stack);
        cargo.removeIf(ItemStack::isEmpty);
        inventory.markDirty();
        DrakeOutpostOwnership.get(world.getServer()).markDirty();
        guard.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        world.playSound(null, destination.chest(), net.minecraft.sound.SoundEvents.BLOCK_CHEST_CLOSE,
                net.minecraft.sound.SoundCategory.BLOCKS, .7f, 1);
        return true;
    }

    private static Inventory inventory(ServerWorld world, BlockPos pos) {
        var state = world.getBlockState(pos);
        if (!(world.getBlockEntity(pos) instanceof ChestBlockEntity) || !(state.getBlock() instanceof ChestBlock chest)) return null;
        return ChestBlock.getInventory(chest, state, world, pos, false);
    }

    private static boolean space(Inventory inventory, ItemStack stack) {
        for (int i = 0; i < inventory.size(); i++) {
            var existing = inventory.getStack(i);
            if (existing.isEmpty() || ItemStack.canCombine(existing, stack)
                    && existing.getCount() < Math.min(inventory.getMaxCountPerStack(), existing.getMaxCount())) return true;
        }
        return false;
    }

    private static void insert(Inventory inventory, ItemStack stack) {
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < inventory.size() && !stack.isEmpty(); i++) {
            var existing = inventory.getStack(i);
            int maximum = Math.min(inventory.getMaxCountPerStack(), stack.getMaxCount());
            if (pass == 0 && !existing.isEmpty() && ItemStack.canCombine(existing, stack)) {
                int moved = Math.min(stack.getCount(), Math.max(0, maximum - existing.getCount()));
                existing.increment(moved); stack.decrement(moved);
            } else if (pass == 1 && existing.isEmpty()) inventory.setStack(i, stack.split(Math.min(stack.getCount(), maximum)));
        }
    }

    NbtCompound write() {
        var tag = new NbtCompound();
        tag.putIntArray("Stable", box(stable));
        if (building != null) tag.putIntArray("Building", box(building));
        var parts = new NbtList();
        for (var room : rooms) { var part = new NbtCompound(); part.putIntArray("Box", box(room)); parts.add(part); }
        tag.put("Rooms", parts);
        tag.putLongArray("Chests", chests.stream().mapToLong(BlockPos::asLong).toArray());
        var items = new NbtList(); for (var stack : cargo) if (!stack.isEmpty()) items.add(stack.writeNbt(new NbtCompound()));
        tag.put("Cargo", items); return tag;
    }

    static DrakeConfiscationStorage read(NbtCompound tag) {
        var stable = readBox(tag.getIntArray("Stable"));
        if (stable == null) return null;
        var result = new DrakeConfiscationStorage(stable);
        result.building = readBox(tag.getIntArray("Building"));
        for (var element : tag.getList("Rooms", NbtElement.COMPOUND_TYPE)) {
            var room = readBox(((NbtCompound)element).getIntArray("Box")); if (room != null) result.rooms.add(room);
        }
        for (long pos : tag.getLongArray("Chests")) result.chests.add(BlockPos.fromLong(pos));
        for (var entry : tag.getList("Cargo", NbtElement.COMPOUND_TYPE)) {
            var stack = ItemStack.fromNbt((NbtCompound)entry); if (!stack.isEmpty()) result.cargo.add(stack);
        }
        return result;
    }

    private static int[] box(BlockBox box) {
        return new int[]{box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ()};
    }
    private static BlockBox readBox(int[] box) {
        return box.length == 6 ? new BlockBox(box[0], box[1], box[2], box[3], box[4], box[5]) : null;
    }
}
