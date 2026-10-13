package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.RecipeBookCategory;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.player.MentalCrafting;

@Mixin(AbstractFurnaceScreenHandler.class)
public abstract class MentalFurnaceMixin {
    @Shadow @Final private RecipeType<? extends AbstractCookingRecipe> recipeType;
    @Inject(method = "<init>(Lnet/minecraft/screen/ScreenHandlerType;Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/book/RecipeBookCategory;ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/screen/PropertyDelegate;)V", at = @At("RETURN"))
    private void sscExtras$foodInput(ScreenHandlerType<?> type, RecipeType<? extends AbstractCookingRecipe> recipeType,
            RecipeBookCategory category, int syncId, PlayerInventory playerInventory, Inventory inventory, PropertyDelegate properties, CallbackInfo ci) {
        var handler = (ScreenHandler)(Object)this;
        Slot original = handler.getSlot(0);
        Slot guarded = new Slot(inventory, 0, original.x, original.y) {
            @Override public boolean canInsert(ItemStack stack) { return MentalCrafting.furnaceInput(playerInventory.player, recipeType, stack); }
        };
        guarded.id = original.id;
        handler.slots.set(0, guarded);
        Slot fuel = handler.getSlot(1);
        Slot guardedFuel = new Slot(inventory, 1, fuel.x, fuel.y) {
            @Override public boolean canInsert(ItemStack stack) {
                return fuel.canInsert(stack) && MentalCrafting.furnaceInput(playerInventory.player, recipeType, inventory.getStack(0));
            }
            @Override public int getMaxItemCount(ItemStack stack) { return fuel.getMaxItemCount(stack); }
        };
        guardedFuel.id = fuel.id;
        handler.slots.set(1, guardedFuel);
    }
    @Inject(method = "quickMove", at = @At("HEAD"), cancellable = true)
    private void sscExtras$foodShift(PlayerEntity player, int index, CallbackInfoReturnable<ItemStack> cir) {
        var handler = (ScreenHandler)(Object)this;
        if (index >= 3 && index < handler.slots.size() && (!MentalCrafting.furnaceInput(player, recipeType, handler.getSlot(index).getStack())
                || !MentalCrafting.furnaceInput(player, recipeType, handler.getSlot(0).getStack()))) cir.setReturnValue(ItemStack.EMPTY);
    }
}
