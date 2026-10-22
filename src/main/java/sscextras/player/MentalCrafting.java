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
    /** Classify the completed recipe using the same requirements and the actual consumed inputs. */
    public static double reward(PlayerEntity player, Recipe<?> recipe, java.util.List<ItemStack> inputs, boolean table) {
        var context = MentalContext.recipe(recipe, player.getWorld().getRegistryManager());
        if (MentalRequirements.required(context) == IntLevel.REGULAR) return 10;
        if (inputs.stream().anyMatch(MentalCrafting::metalIngot)) return 2;
        return table ? 1 : 0;
    }
    private static boolean metalIngot(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.isOf(net.minecraft.item.Items.IRON_INGOT) || stack.isOf(net.minecraft.item.Items.GOLD_INGOT)
                || stack.isOf(net.minecraft.item.Items.COPPER_INGOT) || stack.isOf(net.minecraft.item.Items.NETHERITE_INGOT)) return true;
        return stack.streamTags().anyMatch(tag -> {
            var id = tag.id();
            return (id.getNamespace().equals("forge") || id.getNamespace().equals("c"))
                    && (id.getPath().equals("ingots") || id.getPath().startsWith("ingots/") || id.getPath().endsWith("_ingots"));
        });
    }
    public static boolean furnaceInput(PlayerEntity player, RecipeType<? extends AbstractCookingRecipe> type, ItemStack stack) {
        if (stack.isEmpty()) return true;
        var recipe = player.getWorld().getRecipeManager().getFirstMatch(type, new SimpleInventory(stack), player.getWorld());
        return recipe.isEmpty() || allowed(player, recipe.get());
    }
}
