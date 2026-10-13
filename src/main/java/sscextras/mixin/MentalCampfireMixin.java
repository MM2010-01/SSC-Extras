package sscextras.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.recipe.RecipeType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.player.MentalCrafting;

@Mixin(CampfireBlock.class)
public abstract class MentalCampfireMixin {
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void sscExtras$cookingInt(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand,
            BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        var recipe = world.getRecipeManager().getFirstMatch(RecipeType.CAMPFIRE_COOKING, new SimpleInventory(player.getStackInHand(hand)), world);
        if (recipe.isPresent() && !MentalCrafting.allowed(player, recipe.get())) cir.setReturnValue(ActionResult.FAIL);
    }
}
