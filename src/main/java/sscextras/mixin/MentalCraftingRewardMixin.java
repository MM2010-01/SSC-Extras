package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.slot.CraftingResultSlot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.player.MentalCrafting;
import sscextras.player.MentalProgression;

@Mixin(CraftingResultSlot.class)
public abstract class MentalCraftingRewardMixin {
    @Shadow @Final private RecipeInputInventory input;
    @Unique private Identifier sscExtras$recipe;
    @Unique private double sscExtras$reward;

    @Inject(method = "onTakeItem", at = @At("HEAD"))
    private void sscExtras$captureRecipe(PlayerEntity player, ItemStack result, CallbackInfo ci) {
        sscExtras$recipe = null; sscExtras$reward = 0;
        if (!(player instanceof ServerPlayerEntity serverPlayer) || !MentalProgression.eligible(serverPlayer)) return;
        var recipe = player.getWorld().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, player.getWorld());
        if (recipe.isEmpty()) return;
        sscExtras$recipe = recipe.get().getId();
        sscExtras$reward = MentalCrafting.reward(player, recipe.get(), input.getInputStacks(), input.getWidth() == 3 && input.getHeight() == 3);
    }
    @Inject(method = "onTakeItem", at = @At("TAIL"))
    private void sscExtras$completedRecipe(PlayerEntity player, ItemStack result, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity serverPlayer && sscExtras$recipe != null)
            MentalProgression.crafted(serverPlayer, sscExtras$recipe, sscExtras$reward);
        sscExtras$recipe = null; sscExtras$reward = 0;
    }
}
