package sscextras.drake;

import com.mojang.serialization.Codec;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;
import java.util.Optional;

public final class MountMerchantStructure extends Structure {
    public static final Codec<MountMerchantStructure> CODEC = createCodec(MountMerchantStructure::new);
    public MountMerchantStructure(Config config) { super(config); }
    @Override protected Optional<StructurePosition> getStructurePosition(Context context) {
        var sourceChunk = new ChunkPos(context.chunkPos().x - MountMerchantPlacement.OFFSET_CHUNKS, context.chunkPos().z);
        var sets = context.dynamicRegistryManager().get(RegistryKeys.STRUCTURE_SET);
        var calculator = StructurePlacementCalculator.create(context.noiseConfig(), context.seed(), context.biomeSource(),
                sets.streamEntries().map(entry -> (net.minecraft.registry.entry.RegistryEntry<net.minecraft.structure.StructureSet>)entry));
        for (var set : sets) {
            if (set.structures().stream().noneMatch(entry -> MountMerchantPlacement.source(entry.structure()))
                    || !set.placement().shouldGenerate(calculator, sourceChunk.x, sourceChunk.z)) continue;
            for (var entry : set.structures()) {
                if (!MountMerchantPlacement.source(entry.structure())) continue;
                var source = entry.structure().value();
                var start = source.createStructureStart(context.dynamicRegistryManager(), context.chunkGenerator(), context.biomeSource(),
                        context.noiseConfig(), context.structureTemplateManager(), context.seed(), sourceChunk, 0, context.world(), source.getValidBiomes()::contains);
                for (var piece : start.getChildren()) if (piece instanceof DrakeStablePiece stable) {
                    int x = context.chunkPos().getStartX() + 2, z = context.chunkPos().getStartZ() + 2;
                    int y = height(context, x + 6, z + 5);
                    if (y <= context.chunkGenerator().getSeaLevel()) return Optional.empty();
                    for (int dx : new int[]{0, 12}) for (int dz : new int[]{0, 10})
                        if (Math.abs(height(context, x + dx, z + dz) - y) > 3) return Optional.empty();
                    var pos = new BlockPos(x, y, z);
                    return Optional.of(new StructurePosition(pos, collector -> collector.addPiece(new MountMerchantPiece(pos, stable))));
                }
            }
        }
        return Optional.empty();
    }
    private static int height(Context context, int x, int z) {
        return context.chunkGenerator().getHeight(x, z, Heightmap.Type.WORLD_SURFACE_WG, context.world(), context.noiseConfig()) - 1;
    }
    @Override public StructureType<?> getType() { return MountMerchants.STRUCTURE; }
}
