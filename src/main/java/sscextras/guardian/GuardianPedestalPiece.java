package sscextras.guardian;

import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.*;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

public final class GuardianPedestalPiece extends StructurePiece {
    private int residentKind;
    private boolean weathered;
    public static final StructurePieceType TYPE = (context, nbt) -> new GuardianPedestalPiece(nbt);
    public static void register() { Registry.register(Registries.STRUCTURE_PIECE, RuneboundGuardian.id("guardian_pedestal"), TYPE); }
    public GuardianPedestalPiece(int x, int y, int z) {
        super(TYPE, 0, new BlockBox(x, y, z, x + 3, y + 4, z + 3));
        setOrientation(Direction.SOUTH);
    }
    public GuardianPedestalPiece(int x, int y, int z, int kind, boolean weathered) {
        this(x, y, z); residentKind = kind; this.weathered = weathered;
    }
    public GuardianPedestalPiece(NbtCompound nbt) { super(TYPE, nbt); residentKind = nbt.getInt("ResidentKind"); weathered = nbt.getBoolean("Weathered"); }
    public int residentKind() { return residentKind; }
    @Override protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putInt("ResidentKind", residentKind); nbt.putBoolean("Weathered", weathered);
    }
    public BlockPos pedestal() { return new BlockPos(boundingBox.getMinX() + 1, boundingBox.getMinY() + 1, boundingBox.getMinZ() + 1); }
    @Override public void generate(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator,
            Random random, BlockBox chunkBox, ChunkPos chunk, BlockPos pivot) {
        fillWithOutline(world, chunkBox, 0, 1, 0, 3, 4, 3, AIR, AIR, false);
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) {
            addBlock(world, Blocks.STONE_BRICKS.getDefaultState(), x, 0, z, chunkBox);
            fillDownwards(world, Blocks.COBBLESTONE.getDefaultState(), x, -1, z, chunkBox);
        }
        for (int i = 0; i < 4; i++) addBlock(world, GuardianPedestal.BLOCK.getDefaultState()
                .with(GuardianPedestal.PART, i).with(GuardianPedestal.OFFERING, residentKind == 0), 1 + (i & 1), 1, 1 + (i >> 1), chunkBox);
        if (residentKind != 0 && chunkBox.contains(pedestal()) && world.getBlockEntity(pedestal()) instanceof GuardianOffering offering)
            offering.prepareResident(residentKind, weathered);
    }
}
