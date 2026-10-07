package sscextras.drake;

import net.minecraft.block.Blocks;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LanternBlock;
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

public final class MountMerchantPiece extends StructurePiece {
    private DrakeStablePiece stable;
    private boolean spawned;
    public MountMerchantPiece(BlockPos pos, DrakeStablePiece stable) {
        super(MountMerchants.PIECE, 0, new BlockBox(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 12, pos.getY() + 6, pos.getZ() + 10));
        this.stable = stable; setOrientation(Direction.SOUTH);
    }
    public MountMerchantPiece(NbtCompound tag) {
        super(MountMerchants.PIECE, tag);
        stable = MountMarket.readStable(tag.getCompound("Destination")); spawned = tag.getBoolean("MerchantSpawned");
    }
    @Override protected void writeNbt(StructureContext context, NbtCompound tag) {
        tag.put("Destination", MountMarket.writeStable(stable)); tag.putBoolean("MerchantSpawned", spawned);
    }
    @Override public void generate(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator,
            Random random, BlockBox chunkBox, ChunkPos chunk, BlockPos pivot) {
        fillWithOutline(world, chunkBox, 0, 1, 0, 12, 6, 10, AIR, AIR, false);
        fillWithOutline(world, chunkBox, 0, 0, 0, 12, 0, 10, Blocks.COBBLESTONE.getDefaultState(), Blocks.COARSE_DIRT.getDefaultState(), false);
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 10; z++)
            fillDownwards(world, Blocks.COBBLESTONE.getDefaultState(), x, -1, z, chunkBox);
        fillWithOutline(world, chunkBox, 1, 0, 5, 11, 0, 9, Blocks.HAY_BLOCK.getDefaultState(), Blocks.HAY_BLOCK.getDefaultState(), false);
        for (int y = 1; y <= 2; y++) {
            for (int x = 0; x <= 12; x++) {
                addBlock(world, Blocks.DARK_OAK_FENCE.getDefaultState(), x, y, 10, chunkBox);
                if (x != 8 && x != 9) addBlock(world, Blocks.DARK_OAK_FENCE.getDefaultState(), x, y, 3, chunkBox);
            }
            for (int z = 3; z <= 10; z++) for (int x : new int[]{0, 12}) addBlock(world, Blocks.DARK_OAK_FENCE.getDefaultState(), x, y, z, chunkBox);
        }
        for (int x : new int[]{8, 9}) addBlock(world, Blocks.DARK_OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING, Direction.SOUTH), x, 1, 3, chunkBox);
        for (int x : new int[]{0, 5}) for (int z : new int[]{0, 3})
            fillWithOutline(world, chunkBox, x, 1, z, x, 4, z, Blocks.DARK_OAK_LOG.getDefaultState(), Blocks.DARK_OAK_LOG.getDefaultState(), false);
        fillWithOutline(world, chunkBox, 0, 5, 0, 5, 5, 4, Blocks.BROWN_WOOL.getDefaultState(), Blocks.BROWN_WOOL.getDefaultState(), false);
        fillWithOutline(world, chunkBox, 1, 1, 0, 4, 1, 0, Blocks.DARK_OAK_SLAB.getDefaultState(), Blocks.DARK_OAK_SLAB.getDefaultState(), false);
        addBlock(world, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), 2, 4, 1, chunkBox);
        addBlock(world, Blocks.WATER_CAULDRON.getDefaultState(), 11, 1, 9, chunkBox);
        addBlock(world, Blocks.DARK_OAK_FENCE.getDefaultState(), 6, 1, 7, chunkBox);
        BlockPos pos = new BlockPos(boundingBox.getMinX() + 3, boundingBox.getMinY() + 1, boundingBox.getMinZ() + 2);
        if (!spawned && stable != null && chunkBox.contains(pos)) {
            var merchant = MountMerchants.MERCHANT.create(world.toServerWorld());
            if (merchant != null) {
                merchant.refreshPositionAndAngles(pos, 180, 0);
                merchant.setSite(new BlockPos(boundingBox.getMinX(), boundingBox.getMinY(), boundingBox.getMinZ()), stable);
                spawned = world.spawnEntity(merchant);
            }
        }
    }
}
