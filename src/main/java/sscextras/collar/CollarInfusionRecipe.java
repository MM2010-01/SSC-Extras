package sscextras.collar;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.status_effects.BaseTransformativeStatusEffect;
import net.onixary.shapeShifterCurseFabric.status_effects.transformative_effects.TransformativeStatus;
import net.onixary.shapeShifterCurseFabric.status_effects.transformative_effects.TransformativeStatusPotion;

public final class CollarInfusionRecipe extends SpecialCraftingRecipe {
    public CollarInfusionRecipe(Identifier id, CraftingRecipeCategory category) { super(id, category); }

    private static BaseTransformativeStatusEffect potionEffect(ItemStack stack) {
        if (!stack.isOf(Items.POTION) && !stack.isOf(Items.SPLASH_POTION) && !stack.isOf(Items.LINGERING_POTION)) return null;
        BaseTransformativeStatusEffect found = null;
        for (var effect : PotionUtil.getPotionEffects(stack)) {
            if (!(effect.getEffectType() instanceof TransformativeStatusPotion potion)
                    || !(potion.TransformativeStatusEffect instanceof TransformativeStatus)) continue;
            if (found != null && found != potion.TransformativeStatusEffect) return null;
            found = potion.TransformativeStatusEffect;
        }
        return found;
    }

    @Override public boolean matches(RecipeInputInventory inventory, World world) {
        boolean collar = false, potion = false;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof CollarItem && !collar) collar = true;
            else if (potionEffect(stack) != null && !potion) potion = true;
            else return false;
        }
        return collar && potion;
    }

    @Override public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registries) {
        ItemStack output = ItemStack.EMPTY;
        BaseTransformativeStatusEffect effect = null;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() instanceof CollarItem) output = stack.copyWithCount(1);
            else if (potionEffect(stack) != null) effect = potionEffect(stack);
        }
        if (output.isEmpty() || effect == null) return ItemStack.EMPTY;
        output.getOrCreateNbt().putString(Collars.INFUSION, Registries.STATUS_EFFECT.getId(effect).toString());
        ((CollarItem) output.getItem()).ensureBinding(output);
        return output;
    }

    @Override public DefaultedList<ItemStack> getRemainder(RecipeInputInventory inventory) {
        DefaultedList<ItemStack> remainder = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
        for (int i = 0; i < inventory.size(); i++) {
            if (potionEffect(inventory.getStack(i)) != null) remainder.set(i, new ItemStack(Items.GLASS_BOTTLE));
        }
        return remainder;
    }

    @Override public boolean fits(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return Collars.INFUSION_RECIPE; }
}
