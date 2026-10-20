package sscextras.guardian;

import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** World blocks owned by one Awakening; the village checkpoint owns their lifetime. */
final class GuardianStoneCast {
    private static final int CHISEL_TICKS = 10;
    private final List<BlockPos> remaining = new ArrayList<>();
    private boolean hardened;
    private int chiselTicks;
    private BlockPos cracking;
    private int breaker;

    private static List<BlockPos> volume(BlockPos pedestal) {
        var blocks = new ArrayList<BlockPos>();
        for (int y = 0; y <= 3; y++) for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) {
            if (y == 0 && x >= 0 && x <= 1 && z >= 0 && z <= 1) continue;
            blocks.add(pedestal.add(x, y, z));
        }
        blocks.sort(Comparator.comparingInt(BlockPos::getY).reversed()
                .thenComparing(Comparator.comparingDouble((BlockPos p) -> Math.abs(p.getX() - pedestal.getX() - .5)).reversed())
                .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
        return blocks;
    }

    boolean harden(ServerWorld world, BlockPos pedestal, GuardianShellEntity body) {
        if (hardened) return true;
        var positions = volume(pedestal);
        for (var pos : positions) {
            if (!world.isInBuildLimit(pos) || !world.isChunkLoaded(pos) || !world.getBlockState(pos).isAir()
                    || !world.getOtherEntities(body, new Box(pos), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()).isEmpty())
                return false;
        }
        for (var pos : positions) {
            if (!world.setBlockState(pos, Blocks.STONE.getDefaultState())) {
                clear(world);
                return false;
            }
            remaining.add(pos);
        }
        hardened = true;
        GuardianRitual.sound(world, body.getPos(), SoundEvents.BLOCK_STONE_PLACE, .7f);
        return true;
    }

    void carve(ServerWorld world, List<VillagerEntity> carvers) {
        if (remaining.isEmpty()) return;
        var pos = remaining.get(0);
        if (!world.getBlockState(pos).isOf(Blocks.STONE)) {
            clearCracks(world); remaining.remove(0); chiselTicks = 0; return;
        }
        var target = Vec3d.ofCenter(pos);
        var worker = carvers.stream().min(Comparator.comparingDouble(v -> v.squaredDistanceTo(target))).orElseThrow();
        worker.getLookControl().lookAt(target.x, target.y, target.z);
        if (cracking != null && (!cracking.equals(pos) || breaker != worker.getId())) clearCracks(world);
        cracking = pos; breaker = worker.getId();
        if (chiselTicks % 5 == 0) {
            worker.swingHand(Hand.MAIN_HAND);
            GuardianRitual.sound(world, target, SoundEvents.BLOCK_STONE_HIT, 1);
        }
        world.setBlockBreakingInfo(breaker, pos, chiselTicks * 10 / CHISEL_TICKS);
        if (++chiselTicks >= CHISEL_TICKS) {
            clearCracks(world);
            if (world.breakBlock(pos, false, worker)) remaining.remove(0);
            chiselTicks = 0;
        }
    }

    boolean finished() { return hardened && remaining.isEmpty(); }

    void clearCracks(ServerWorld world) {
        if (cracking != null) world.setBlockBreakingInfo(breaker, cracking, -1);
        cracking = null;
    }

    void clear(ServerWorld world) {
        clearCracks(world);
        for (var pos : remaining) if (world.getBlockState(pos).isOf(Blocks.STONE)) world.removeBlock(pos, false);
        remaining.clear(); hardened = false; chiselTicks = 0;
    }

    NbtCompound write() {
        var nbt = new NbtCompound();
        nbt.putBoolean("Hardened", hardened);
        nbt.putInt("ChiselTicks", chiselTicks);
        nbt.putLongArray("Remaining", remaining.stream().mapToLong(BlockPos::asLong).toArray());
        return nbt;
    }

    void read(NbtCompound nbt, BlockPos pedestal) {
        remaining.clear();
        var allowed = volume(pedestal);
        for (long encoded : nbt.getLongArray("Remaining")) {
            var pos = BlockPos.fromLong(encoded);
            if (allowed.contains(pos) && !remaining.contains(pos)) remaining.add(pos);
        }
        hardened = nbt.getBoolean("Hardened");
        chiselTicks = Math.max(0, Math.min(CHISEL_TICKS - 1, nbt.getInt("ChiselTicks")));
    }
}
