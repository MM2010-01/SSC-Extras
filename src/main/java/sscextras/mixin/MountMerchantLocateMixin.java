package sscextras.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.drake.MountMerchantPlacement;
import java.util.ArrayList;
import java.util.List;

@Mixin(ChunkGenerator.class)
public abstract class MountMerchantLocateMixin {
    @ModifyExpressionValue(method = "locateStructure(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/registry/entry/RegistryEntryList;Lnet/minecraft/util/math/BlockPos;IZ)Lcom/mojang/datafixers/util/Pair;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/chunk/placement/StructurePlacementCalculator;getPlacements(Lnet/minecraft/registry/entry/RegistryEntry;)Ljava/util/List;"))
    private List<StructurePlacement> sscExtras$merchantSearch(List<StructurePlacement> original, ServerWorld world) {
        if (original.stream().noneMatch(MountMerchantPlacement.class::isInstance)) return original;
        var expanded = new ArrayList<StructurePlacement>();
        for (var placement : original) {
            if (placement instanceof MountMerchantPlacement merchant)
                expanded.addAll(merchant.locatePlacements(world.getChunkManager().getStructurePlacementCalculator()));
            else expanded.add(placement);
        }
        return expanded;
    }
}
