package sscextras.collar;

import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
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
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.status_effects.CTPUtils;
import net.onixary.shapeShifterCurseFabric.status_effects.transformative_effects.CustomTransformativeStatue;
import net.onixary.shapeShifterCurseFabric.status_effects.transformative_effects.TransformativeStatusPotion;

public final class CollarInfusionRecipe extends SpecialCraftingRecipe {
    public CollarInfusionRecipe(Identifier id, CraftingRecipeCategory category) { super(id, category); }

    private record Infusion(BaseTransformativeStatusEffect effect, Identifier form) { }

    private static Infusion potionInfusion(ItemStack stack) {
        if (!(stack.getItem() instanceof PotionItem)) return null;
        BaseTransformativeStatusEffect found = null;
        for (var effect : PotionUtil.getPotionEffects(stack)) {
            var type = effect.getEffectType();
            BaseTransformativeStatusEffect curse = type instanceof TransformativeStatusPotion potion
                    ? potion.TransformativeStatusEffect : type instanceof BaseTransformativeStatusEffect direct ? direct : null;
            if (curse == null) continue;
            if (found != null && found != curse) return null;
            found = curse;
        }
        if (found == null) return null;
        Identifier form = null;
        if (found instanceof CustomTransformativeStatue) {
            form = CTPUtils.getCTPFormIDFromNBT(stack.getNbt());
            if (form == null || !Collars.validForm(RegPlayerForms.getPlayerForm(form))) return null;
        }
        return new Infusion(found, form);
    }

    @Override public boolean matches(RecipeInputInventory inventory, World world) {
        boolean collar = false, potion = false;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof CollarItem && !collar) collar = true;
            else if (potionInfusion(stack) != null && !potion) potion = true;
            else return false;
        }
        return collar && potion;
    }

    @Override public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registries) {
        ItemStack output = ItemStack.EMPTY;
        Infusion infusion = null;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.getItem() instanceof CollarItem) output = stack.copyWithCount(1);
            else if (!stack.isEmpty()) infusion = potionInfusion(stack);
        }
        if (output.isEmpty() || infusion == null) return ItemStack.EMPTY;
        output.getOrCreateNbt().putString(Collars.INFUSION, Registries.STATUS_EFFECT.getId(infusion.effect()).toString());
        if (infusion.form() == null) output.getOrCreateNbt().remove(Collars.INFUSION_FORM);
        else output.getOrCreateNbt().putString(Collars.INFUSION_FORM, infusion.form().toString());
        ((CollarItem) output.getItem()).ensureBinding(output);
        return output;
    }

    @Override public DefaultedList<ItemStack> getRemainder(RecipeInputInventory inventory) {
        DefaultedList<ItemStack> remainder = DefaultedList.ofSize(inventory.size(), ItemStack.EMPTY);
        for (int i = 0; i < inventory.size(); i++) {
            if (potionInfusion(inventory.getStack(i)) != null) remainder.set(i, new ItemStack(Items.GLASS_BOTTLE));
        }
        return remainder;
    }

    @Override public boolean fits(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return Collars.INFUSION_RECIPE; }
}
