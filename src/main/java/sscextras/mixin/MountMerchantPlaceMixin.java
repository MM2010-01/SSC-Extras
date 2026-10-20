package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.PlaceCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.structure.StructureStart;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.drake.MountMerchantStructure;
import java.util.function.Predicate;

@Mixin(PlaceCommand.class)
public abstract class MountMerchantPlaceMixin {
    @WrapOperation(method = "executePlaceStructure", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/structure/Structure;createStructureStart(Lnet/minecraft/registry/DynamicRegistryManager;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/world/biome/source/BiomeSource;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/structure/StructureTemplateManager;JLnet/minecraft/util/math/ChunkPos;ILnet/minecraft/world/HeightLimitView;Ljava/util/function/Predicate;)Lnet/minecraft/structure/StructureStart;"))
    private static StructureStart sscExtras$placeMerchant(Structure structure, DynamicRegistryManager registries,
            ChunkGenerator generator, BiomeSource biomes, NoiseConfig noise, StructureTemplateManager templates,
            long seed, ChunkPos chunk, int references, HeightLimitView world, Predicate<RegistryEntry<Biome>> allowed,
            Operation<StructureStart> original, ServerCommandSource source, RegistryEntry.Reference<Structure> entry, BlockPos pos) {
        if (structure instanceof MountMerchantStructure merchant) return merchant.commandStart(source.getWorld(), pos);
        return original.call(structure, registries, generator, biomes, noise, templates, seed, chunk, references, world, allowed);
    }
}
