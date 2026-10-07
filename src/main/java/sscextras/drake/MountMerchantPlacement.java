package sscextras.drake;

import com.mojang.serialization.Codec;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.chunk.placement.StructurePlacementType;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

public final class MountMerchantPlacement extends StructurePlacement {
    public static final int OFFSET_CHUNKS = 16;
    public static final Codec<MountMerchantPlacement> CODEC = Codec.unit(MountMerchantPlacement::new);
    public MountMerchantPlacement() { super(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1, 0, Optional.empty()); }
    static boolean source(net.minecraft.registry.entry.RegistryEntry<net.minecraft.world.gen.structure.Structure> structure) {
        return structure.getKey().map(key -> key.getValue().toString()).filter(id ->
                id.equals("minecraft:pillager_outpost") || id.equals("minecraft:mansion")).isPresent();
    }
    @Override protected boolean isStartChunk(StructurePlacementCalculator calculator, int x, int z) {
        for (var set : calculator.getStructureSets()) {
            if (set.value().structures().stream().anyMatch(entry -> source(entry.structure()))
                    && set.value().placement().shouldGenerate(calculator, x - OFFSET_CHUNKS, z)) return true;
        }
        return false;
    }
    public List<StructurePlacement> locatePlacements(StructurePlacementCalculator calculator) {
        var placements = new ArrayList<StructurePlacement>();
        for (var set : calculator.getStructureSets()) {
            if (!(set.value().placement() instanceof RandomSpreadStructurePlacement spread)
                    || set.value().structures().stream().noneMatch(entry -> source(entry.structure()))) continue;
            // Locate only understands native spread/ring placements. Delegate each source's seeded grid.
            placements.add(new RandomSpreadStructurePlacement(spread.getSpacing(), spread.getSeparation(), spread.getSpreadType(), 0) {
                @Override public ChunkPos getStartChunk(long seed, int x, int z) {
                    var source = spread.getStartChunk(seed, x - OFFSET_CHUNKS, z);
                    return new ChunkPos(source.x + OFFSET_CHUNKS, source.z);
                }
            });
        }
        return placements;
    }
    @Override public StructurePlacementType<?> getType() { return MountMerchants.PLACEMENT; }
}
