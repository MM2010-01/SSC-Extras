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
    private final boolean[] spawned;
    private final String[] names;

    public DrakeStablePiece(int x, int y, int z) {
        this(x, y, z, 3);
    }

    public DrakeStablePiece(int x, int y, int z, int stalls) {
        this(x, y, z, stalls, stalls == 6);
    }

    public DrakeStablePiece(int x, int y, int z, int stalls, boolean facingRows) {
        super(DrakeStable.PIECE, 0, new BlockBox(x, y, z, x + (facingRows ? 21 : stalls * 7),
                y + (facingRows ? 11 : 7), z + (facingRows ? 26 : 12)));
        spawned = new boolean[residentCount()];
        names = new String[residentCount()];
        java.util.Arrays.fill(names, "");
        setOrientation(Direction.SOUTH);
    }

    public DrakeStablePiece(NbtCompound nbt) {
        super(DrakeStable.PIECE, nbt);
        spawned = new boolean[residentCount()];
        names = new String[residentCount()];
        for (int i = 0; i < names.length; i++) {
            var key = i == 0 ? "First" : i == 1 ? "Second" : "Resident" + i;
            spawned[i] = nbt.getBoolean(key + "Drake");
            names[i] = nbt.getString(key + "Name");
        }
    }

    @Override protected void writeNbt(StructureContext context, NbtCompound nbt) {
        ensureNames();
        for (int i = 0; i < names.length; i++) {
            var key = i == 0 ? "First" : i == 1 ? "Second" : "Resident" + i;
            nbt.putBoolean(key + "Drake", spawned[i]);
            nbt.putString(key + "Name", names[i]);
        }
    }

    @Override public void generate(StructureWorldAccess world, StructureAccessor accessor, ChunkGenerator generator,
            Random random, BlockBox chunkBox, ChunkPos chunk, BlockPos pivot) {
        var box = getBoundingBox();
        boolean facing = DrakeStableLayout.facingRows(box);
        int end = box.getBlockCountX() - 1, depth = box.getBlockCountZ() - 1;
        int eaves = facing ? 7 : 5;
        fillWithOutline(world, chunkBox, 0, 1, 0, end, box.getBlockCountY() - 1, depth, AIR, AIR, false);
        fillWithOutline(world, chunkBox, 0, 0, 0, end, 0, depth, Blocks.COBBLESTONE.getDefaultState(), Blocks.COARSE_DIRT.getDefaultState(), false);
        for (int z = 0; z <= depth; z++) {
            int height = eaves + Math.min(z, depth - z) / 3;
            fillWithOutline(world, chunkBox, 0, height, z, end, height, z,
                    Blocks.DARK_OAK_PLANKS.getDefaultState(), Blocks.DARK_OAK_PLANKS.getDefaultState(), false);
        }
        for (int row = 0; row < (facing ? 2 : 1); row++) {
            int first = row * 3;
            int front = DrakeStableLayout.rowZ(box, first, 3), back = DrakeStableLayout.rowZ(box, first, 12);
            for (int x = 0; x <= end; x++) for (int y = 1; y <= 2; y++) {
                addBlock(world, Blocks.DARK_OAK_FENCE.getDefaultState(), x, y, back, chunkBox);
                if (x % 7 != 3 && x % 7 != 4)
                    addBlock(world, Blocks.DARK_OAK_FENCE.getDefaultState(), x, y, front, chunkBox);
            }
            for (int x = 0; x <= end; x += 7) for (int z = Math.min(front, back); z <= Math.max(front, back); z++)
                for (int y = 1; y <= 2; y++) addBlock(world, Blocks.DARK_OAK_FENCE.getDefaultState(), x, y, z, chunkBox);
            for (int x = 3; x < end; x++) if (x % 7 == 3 || x % 7 == 4) addBlock(world,
                    Blocks.DARK_OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING, back > front ? Direction.SOUTH : Direction.NORTH), x, 1, front, chunkBox);
            for (int x = 2; x < end; x += 7) {
                int hayA = DrakeStableLayout.rowZ(box, first, 6), hayB = DrakeStableLayout.rowZ(box, first, 10);
                fillWithOutline(world, chunkBox, x, 0, Math.min(hayA, hayB), x + 3, 0, Math.max(hayA, hayB),
                        Blocks.HAY_BLOCK.getDefaultState(), Blocks.HAY_BLOCK.getDefaultState(), false);
                addBlock(world, Blocks.WATER_CAULDRON.getDefaultState(), x + 3, 1, DrakeStableLayout.rowZ(box, first, 11), chunkBox);
                int lanternZ = DrakeStableLayout.rowZ(box, first, 1);
                addBlock(world, Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), x + 1,
                        eaves + Math.min(lanternZ, depth - lanternZ) / 3 - 1, lanternZ, chunkBox);
            }
        }
        // Build supports last so the stall fencing cannot replace their lower logs.
        for (int x = 0; x <= end; x += 7) for (int z : new int[]{0, depth}) {
            fillWithOutline(world, chunkBox, x, 1, z, x, eaves - 1, z, Blocks.DARK_OAK_LOG.getDefaultState(), Blocks.DARK_OAK_LOG.getDefaultState(), false);
            fillDownwards(world, Blocks.COBBLESTONE.getDefaultState(), x, -1, z, chunkBox);
        }
        addChest(world, chunkBox, random, 1, 1, facing ? 13 : 1, EarthenDrake.id("chests/drake_stable"));
        for (int stall = 0; stall < stallCount(); stall++) {
            var pos = sign(stall);
            addBlock(world, Blocks.DARK_OAK_SIGN.getDefaultState().with(SignBlock.ROTATION,
                    DrakeStableLayout.inward(box, gate(stall)) < 0 ? 0 : 8), pos.getX() - box.getMinX(), 1, pos.getZ() - box.getMinZ(), chunkBox);
            if (chunkBox.contains(pos) && world.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                var text = new SignText().withMessage(1, Text.literal(stall < residentCount() ? name(stall) : ""));
                // Generation block entities have no world yet; live text setters send world updates.
                var encoded = SignText.CODEC.encodeStart(NbtOps.INSTANCE, text).result().orElseThrow();
                var data = sign.createNbt();
                data.put("front_text", encoded); data.put("back_text", encoded.copy());
                sign.readNbt(data);
            }
        }
        for (int i = 0; i < residentCount(); i++)
            if (!spawned[i]) spawned[i] = spawn(world, chunkBox, i, name(i));
    }

    public int stallCount() { return DrakeStableLayout.stallCount(getBoundingBox()); }
    public int residentCount() { return stallCount() == 2 ? 2 : stallCount() - 1; }
    public BlockPos reservedStall() { return stallCount() >= 3 ? bed(stallCount() - 1) : null; }
    public BlockPos gate(int stall) { return DrakeStableLayout.gate(getBoundingBox(), stall); }
    public BlockPos reservedGate() { return gate(stallCount() - 1); }
    public BlockPos gateAt(net.minecraft.util.math.Vec3d position) {
        var box = getBoundingBox();
        if (position.y < box.getMinY() || position.y > box.getMaxY() + 1) return null;
        int column = (int)Math.floor((position.x - box.getMinX()) / 7);
        int columns = (box.getBlockCountX() - 1) / 7;
        if (column < 0 || column >= columns) return null;
        for (int stall = column; stall < stallCount(); stall += columns) {
            int gate = gate(stall).getZ(), tie = tie(stall).getZ();
            if (position.z >= Math.min(gate, tie) && position.z <= Math.max(gate, tie) + 1) return gate(stall);
        }
        return null;
    }
    public boolean isGate(BlockPos pos) {
        for (int stall = 0; stall < stallCount(); stall++)
            if (pos.equals(gate(stall)) || pos.equals(gate(stall).east())) return true;
        return false;
    }
    public BlockPos reservedTie() { return tie(stallCount() - 1); }
    public BlockPos tie(int stall) { return DrakeStableLayout.tie(getBoundingBox(), stall); }
    public BlockPos bed(int stall) { return DrakeStableLayout.bed(getBoundingBox(), stall); }
    public BlockPos keeperPosition(int stall) { return DrakeStableLayout.position(getBoundingBox(), stall, 3, 10); }
    public net.minecraft.util.math.Box stall(int stall) { return DrakeStableLayout.stall(getBoundingBox(), stall); }
    public BlockPos sign(int stall) { return DrakeStableLayout.sign(getBoundingBox(), stall); }
    public String firstName() { return name(0); }
    public String secondName() { return name(1); }
    public String name(int stall) { ensureNames(); return names[stall]; }
    public String[] residentNames() { ensureNames(); return names.clone(); }

    private void ensureNames() {
        var random = Random.create(getBoundingBox().getCenter().asLong());
        for (int i = 0; i < names.length; i++)
            if (names[i].isEmpty()) names[i] = DrakeMountNames.choose(random, names);
    }

    private boolean spawn(StructureWorldAccess world, BlockBox chunkBox, int stall, String name) {
        BlockPos pos = bed(stall);
        if (!chunkBox.contains(pos)) return false;
        var drake = DrakeStable.DRAKE.create(world.toServerWorld());
        if (drake == null) return false;
        drake.refreshPositionAndAngles(pos.getX() + .5, pos.getY(), pos.getZ() + .5, DrakeStableLayout.outwardYaw(getBoundingBox(), gate(stall)), 0);
        drake.setPersistent();
        drake.setCustomName(Text.literal(name));
        drake.setStableHome(this, stall);
        world.spawnEntityAndPassengers(drake);
        return true;
    }
}
