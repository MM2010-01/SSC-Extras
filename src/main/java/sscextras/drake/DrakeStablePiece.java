package sscextras.drake;

import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePiece;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

public final class DrakeStablePiece extends StructurePiece {
    private boolean firstDrake, secondDrake;

    public DrakeStablePiece(int x, int y, int z) {
        super(DrakeStable.PIECE, 0, new BlockBox(x, y, z, x + 21, y + 7, z + 12));
        setOrientation(Direction.SOUTH);
    }

    public DrakeStablePiece(NbtCompound nbt) {
        super(DrakeStable.PIECE, nbt);
        firstDrake = nbt.getBoolean("FirstDrake");
        secondDrake = nbt.getBoolean("SecondDrake");
    }

    @Override protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putBoolean("FirstDrake", firstDrake);
        nbt.putBoolean("SecondDrake", secondDrake);
    }

    @Override public void generate(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator,
            Random random, BlockBox chunkBox, ChunkPos chunk, BlockPos pivot) {
        int end = getBoundingBox().getBlockCountX() - 1;
        fillWithOutline(world, chunkBox, 0, 1, 0, end, 7, 12, AIR, AIR, false);
        fillWithOutline(world, chunkBox, 0, 0, 0, end, 0, 12, Blocks.COBBLESTONE.getDefaultState(), Blocks.COARSE_DIRT.getDefaultState(), false);
        for (int x = 0; x <= end; x += 7) for (int z : new int[]{0, 12}) {
            fillWithOutline(world, chunkBox, x, 1, z, x, 4, z, Blocks.DARK_OAK_LOG.getDefaultState(), Blocks.DARK_OAK_LOG.getDefaultState(), false);
            fillDownwards(world, Blocks.COBBLESTONE.getDefaultState(), x, -1, z, chunkBox);
        }
        for (int z = 0; z <= 12; z++) {
            int height = 5 + Math.min(z, 12 - z) / 3;
            fillWithOutline(world, chunkBox, 0, height, z, end, height, z,
                    Blocks.DARK_OAK_PLANKS.getDefaultState(), Blocks.DARK_OAK_PLANKS.getDefaultState(), false);
        }
        for (int x = 0; x <= end; x++) for (int y = 1; y <= 2; y++) {
            addBlock(world, Blocks.DARK_OAK_FENCE.getDefaultState(), x, y, 12, chunkBox);
            if (x % 7 != 3 && x % 7 != 4)
                addBlock(world, Blocks.DARK_OAK_FENCE.getDefaultState(), x, y, 3, chunkBox);
        }
        for (int x = 0; x <= end; x += 7) for (int z = 3; z < 12; z++)
            for (int y = 1; y <= 2; y++) addBlock(world, Blocks.DARK_OAK_FENCE.getDefaultState(), x, y, z, chunkBox);
        for (int x = 3; x < end; x++) if (x % 7 == 3 || x % 7 == 4) addBlock(world,
                Blocks.DARK_OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING, Direction.SOUTH), x, 1, 3, chunkBox);
        for (int x = 2; x < end; x += 7) {
            fillWithOutline(world, chunkBox, x, 0, 6, x + 3, 0, 10, Blocks.HAY_BLOCK.getDefaultState(), Blocks.HAY_BLOCK.getDefaultState(), false);
            addBlock(world, Blocks.WATER_CAULDRON.getDefaultState(), x + 3, 1, 11, chunkBox);
            addBlock(world, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), x + 1, 4, 1, chunkBox);
        }
        // Build the supports last so the stall fencing cannot replace their lower logs.
        for (int x = 0; x <= end; x += 7) for (int z : new int[]{0, 12})
            fillWithOutline(world, chunkBox, x, 1, z, x, 4, z, Blocks.DARK_OAK_LOG.getDefaultState(), Blocks.DARK_OAK_LOG.getDefaultState(), false);
        addChest(world, chunkBox, random, 1, 1, 1, EarthenDrake.id("chests/drake_stable"));
        if (!firstDrake) firstDrake = spawn(world, chunkBox, 3, 8);
        if (!secondDrake) secondDrake = spawn(world, chunkBox, 10, 8);
    }

    public BlockPos reservedStall() { return getBoundingBox().getBlockCountX() >= 22 ? offsetPos(17, 1, 8) : null; }
    public BlockPos reservedGate() { return offsetPos(17, 1, 3); }
    public BlockPos reservedTie() { return offsetPos(17, 1, 12); }

    private boolean spawn(StructureWorldAccess world, BlockBox chunkBox, int x, int z) {
        BlockPos pos = offsetPos(x, 1, z);
        if (!chunkBox.contains(pos)) return false;
        var drake = DrakeStable.DRAKE.create(world.toServerWorld());
        if (drake == null) return false;
        drake.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 180, 0);
        drake.setPersistent();
        drake.setPositionTarget(pos, 16);
        world.spawnEntityAndPassengers(drake);
        return true;
    }
}
