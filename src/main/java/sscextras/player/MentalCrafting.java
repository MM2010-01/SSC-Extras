package sscextras.player;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeType;

public final class MentalCrafting {
    private MentalCrafting() { }
    public static boolean allowed(PlayerEntity player, Recipe<?> recipe) {
        return PlayerMind.allow(player, MentalContext.recipe(recipe, player.getWorld().getRegistryManager()));
    }
    public static boolean furnaceInput(PlayerEntity player, RecipeType<? extends AbstractCookingRecipe> type, ItemStack stack) {
        if (stack.isEmpty()) return true;
        var recipe = player.getWorld().getRecipeManager().getFirstMatch(type, new SimpleInventory(stack), player.getWorld());
        return recipe.isEmpty() || allowed(player, recipe.get());
    }
}
