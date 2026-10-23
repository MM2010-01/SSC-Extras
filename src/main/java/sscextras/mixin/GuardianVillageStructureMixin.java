package sscextras.mixin;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.structure.*;
import net.minecraft.util.math.*;
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
import sscextras.guardian.GuardianPedestalPiece;
import java.util.ArrayList;
import java.util.function.Predicate;

@Mixin(Structure.class)
public abstract class GuardianVillageStructureMixin {
    @Inject(method = "createStructureStart", at = @At("RETURN"), cancellable = true)
    private void sscExtras$villagePedestal(DynamicRegistryManager registries, ChunkGenerator generator, BiomeSource biomes,
            NoiseConfig noise, StructureTemplateManager templates, long seed, ChunkPos chunk, int references,
            HeightLimitView world, Predicate<RegistryEntry<Biome>> allowed, CallbackInfoReturnable<StructureStart> cir) {
        var start = cir.getReturnValue();
        if (!start.hasChildren() || !registries.get(RegistryKeys.STRUCTURE).getEntry((Structure)(Object)this).isIn(StructureTags.VILLAGE)
                || start.getChildren().stream().anyMatch(p -> p instanceof GuardianPedestalPiece)) return;
        var center = start.getChildren().get(0).getBoundingBox().getCenter();
        var pieces = new ArrayList<>(start.getChildren());
        var random = net.minecraft.util.math.random.Random.create(seed ^ chunk.toLong() ^ 0x475541524449414eL);
        for (int kind = 0; kind <= 2; kind++) {
            if (kind != 0 && !random.nextBoolean()) continue;
            GuardianPedestalPiece pedestal = null;
            search: for (int radius = 8; radius <= 112; radius += 4) for (int step = 0; step < 16; step++) {
                double angle = step * Math.PI / 8;
                int x = center.getX() + (int)Math.round(Math.cos(angle) * radius), z = center.getZ() + (int)Math.round(Math.sin(angle) * radius);
                int y = generator.getHeight(x + 1, z + 1, Heightmap.Type.WORLD_SURFACE_WG, world, noise) - 1;
                if (y < generator.getSeaLevel()) continue;
                var bounds = new BlockBox(x - 1, world.getBottomY(), z - 1, x + 4, world.getTopY() - 1, z + 4);
                if (pieces.stream().anyMatch(p -> p.getBoundingBox().intersects(bounds))) continue;
                int farY = generator.getHeight(x + 3, z + 3, Heightmap.Type.WORLD_SURFACE_WG, world, noise) - 1;
                if (Math.abs(y - farY) > 2) continue;
                pedestal = new GuardianPedestalPiece(x, Math.max(y, farY), z, kind, random.nextBoolean()); break search;
            }
            if (pedestal == null && kind == 0) {
                int x = start.getBoundingBox().getMaxX() + 3, z = center.getZ();
                int y = generator.getHeight(x + 1, z + 1, Heightmap.Type.WORLD_SURFACE_WG, world, noise) - 1;
                pedestal = new GuardianPedestalPiece(x, y, z);
            }
            if (pedestal != null) pieces.add(pedestal);
        }
        cir.setReturnValue(new StructureStart(start.getStructure(), start.getPos(), start.getReferences(), new StructurePiecesList(pieces)));
    }
}
