package sscextras.mixin;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.drake.DrakeStablePiece;
import java.util.ArrayList;
import java.util.function.Predicate;

@Mixin(Structure.class)
public abstract class DrakeStableStructureMixin {
    @Inject(method = "createStructureStart", at = @At("RETURN"), cancellable = true)
    private void sscExtras$outpostStable(DynamicRegistryManager registries, ChunkGenerator generator, BiomeSource biomes,
            NoiseConfig noise, StructureTemplateManager templates, long seed, ChunkPos chunk, int references,
            HeightLimitView world, Predicate<RegistryEntry<Biome>> allowed, CallbackInfoReturnable<StructureStart> cir) {
        StructureStart start = cir.getReturnValue();
        var id = registries.get(RegistryKeys.STRUCTURE).getId((Structure)(Object)this);
        boolean mansion = new Identifier("minecraft", "mansion").equals(id);
        if (!start.hasChildren() || !mansion && !new Identifier("minecraft", "pillager_outpost").equals(id)) return;
        var bounds = start.getBoundingBox();
        int stalls = mansion ? 6 : 3;
        int x = bounds.getMaxX() + 6, z = bounds.getCenter().getZ() - 6;
        int y = generator.getHeight(x + stalls * 7 / 2, z + 6, Heightmap.Type.WORLD_SURFACE_WG, world, noise) - 1;
        var pieces = new ArrayList<>(start.getChildren());
        pieces.add(new DrakeStablePiece(x, y, z, stalls));
        cir.setReturnValue(new StructureStart(start.getStructure(), start.getPos(), start.getReferences(), new StructurePiecesList(pieces)));
    }
}
