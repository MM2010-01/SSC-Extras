package sscextras.drake;

import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.text.Text;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
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
    private String firstName = "", secondName = "";

    public DrakeStablePiece(int x, int y, int z) {
        super(DrakeStable.PIECE, 0, new BlockBox(x, y, z, x + 21, y + 7, z + 12));
        setOrientation(Direction.SOUTH);
    }

    public DrakeStablePiece(NbtCompound nbt) {
        super(DrakeStable.PIECE, nbt);
        firstDrake = nbt.getBoolean("FirstDrake");
        secondDrake = nbt.getBoolean("SecondDrake");
        firstName = nbt.getString("FirstName"); secondName = nbt.getString("SecondName");
    }

    @Override protected void writeNbt(StructureContext context, NbtCompound nbt) {
        nbt.putBoolean("FirstDrake", firstDrake);
        nbt.putBoolean("SecondDrake", secondDrake);
        nbt.putString("FirstName", firstName()); nbt.putString("SecondName", secondName());
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
        for (int stall = 0; stall < end / 7; stall++) {
            int x = stall * 7 + 2;
            addBlock(world, Blocks.DARK_OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, 8), x, 1, 2, chunkBox);
            var pos = offsetPos(x, 1, 2);
            if (chunkBox.contains(pos) && world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                var text = new SignText().withMessage(1, Text.literal(stall == 0 ? firstName() : stall == 1 ? secondName() : ""));
                // Generation block entities have no world yet; live text setters send world updates.
                var encoded = SignText.CODEC.encodeStart(NbtOps.INSTANCE, text).result().orElseThrow();
                var data = sign.createNbt();
                data.put("front_text", encoded); data.put("back_text", encoded.copy());
                sign.readNbt(data);
            }
        }
        if (!firstDrake) firstDrake = spawn(world, chunkBox, 3, 8, firstName());
        if (!secondDrake) secondDrake = spawn(world, chunkBox, 10, 8, secondName());
    }

    public BlockPos reservedStall() { return getBoundingBox().getBlockCountX() >= 22 ? offsetPos(17, 1, 8) : null; }
    public BlockPos gate(int stall) { return offsetPos(stall * 7 + 3, 1, 3); }
    public BlockPos reservedGate() { return gate(2); }
    public BlockPos gateAt(net.minecraft.util.math.Vec3d position) {
        var box = getBoundingBox();
        int stall = (int)Math.floor((position.x - box.getMinX()) / 7);
        return stall >= 0 && stall < (box.getBlockCountX() - 1) / 7
                && position.z >= gate(stall).getZ() && position.z <= box.getMaxZ() + 1
                && position.y >= box.getMinY() && position.y <= box.getMaxY() + 1 ? gate(stall) : null;
    }
    public boolean isGate(BlockPos pos) {
        for (int stall = 0; stall < (getBoundingBox().getBlockCountX() - 1) / 7; stall++)
            if (pos.equals(gate(stall)) || pos.equals(gate(stall).east())) return true;
        return false;
    }
    public BlockPos reservedTie() { return offsetPos(17, 1, 12); }
    public BlockPos sign(int stall) { return offsetPos(stall * 7 + 2, 1, 2); }
    public String firstName() { ensureNames(); return firstName; }
    public String secondName() { ensureNames(); return secondName; }

    private void ensureNames() {
        if (!firstName.isEmpty() && !secondName.isEmpty()) return;
        var random = Random.create(getBoundingBox().getCenter().asLong());
        if (firstName.isEmpty()) firstName = DrakeMountNames.choose(random);
        if (secondName.isEmpty()) secondName = DrakeMountNames.choose(random, firstName);
    }

    private boolean spawn(StructureWorldAccess world, BlockBox chunkBox, int x, int z, String name) {
        BlockPos pos = offsetPos(x, 1, z);
        if (!chunkBox.contains(pos)) return false;
        var drake = DrakeStable.DRAKE.create(world.toServerWorld());
        if (drake == null) return false;
        drake.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + .5, 180, 0);
        drake.setPersistent();
        drake.setCustomName(Text.literal(name));
        drake.setStableHome(this, x / 7);
        drake.setPositionTarget(pos, 16);
        world.spawnEntityAndPassengers(drake);
        return true;
    }
}
