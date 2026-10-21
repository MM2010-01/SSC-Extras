package sscextras.petrification;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.*;
import net.minecraft.potion.PotionUtil;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;

public final class PetrificationBucketRecipe extends SpecialCraftingRecipe {
    public PetrificationBucketRecipe(Identifier id, CraftingRecipeCategory category) { super(id, category); }
    @Override public boolean matches(RecipeInputInventory inventory, World world) {
        int bottles = 0, buckets = 0;
        for (int i = 0; i < inventory.size(); i++) {
            var stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.isOf(Items.BUCKET)) buckets++;
            else if (stack.isOf(Items.POTION) && PotionUtil.getPotion(stack) == Petrification.POTION
                    && PotionUtil.getCustomPotionEffects(stack).isEmpty()) bottles++;
            else return false;
        }
        return bottles == 3 && buckets == 1;
    }
    @Override public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registries) { return new ItemStack(Petrification.BUCKET); }
    @Override public boolean fits(int width, int height) { return width * height >= 4; }
    @Override public RecipeSerializer<?> getSerializer() { return Petrification.BUCKET_RECIPE; }
    @Override public DefaultedList<ItemStack> getRemainder(RecipeInputInventory inventory) {
        var remainder = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
        for (int i = 0; i < inventory.size(); i++) if (inventory.getStack(i).isOf(Items.POTION)) remainder.set(i, new ItemStack(Items.GLASS_BOTTLE));
        return remainder;
    }
}
