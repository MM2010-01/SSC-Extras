package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import sscextras.effigy.InfusionHand;
import sscextras.effigy.Infusions;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class InfusionMiningMixin {
    @Shadow protected ServerPlayerEntity player;

    @WrapMethod(method = "tryBreakBlock")
    private boolean sscExtras$infusedMining(BlockPos pos, Operation<Boolean> original) {
        ItemStack stack = Infusions.tool(player, player.getWorld().getBlockState(pos));
        if (stack.isEmpty()) return original.call(pos);
        try (var ignored = new InfusionHand(player, stack)) {
            return original.call(pos);
        } finally {
            Infusions.changed(player);
        }
    }
}
