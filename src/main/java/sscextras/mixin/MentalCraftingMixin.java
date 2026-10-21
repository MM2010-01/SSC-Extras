package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.recipe.RecipeType;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.player.*;

@Mixin(CraftingScreenHandler.class)
public abstract class MentalCraftingMixin {
    @Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
    private static void sscExtras$recipeInt(ScreenHandler handler, World world, PlayerEntity player,
            RecipeInputInventory input, CraftingResultInventory result, CallbackInfo ci) {
        if (world.isClient || PlayerMind.permits(player, IntLevel.REGULAR)) return;
        var recipe = world.getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, world);
        if (recipe.isEmpty() || MentalCrafting.allowed(player, recipe.get())) return;
        result.setStack(0, ItemStack.EMPTY);
        handler.setPreviousTrackedSlot(0, ItemStack.EMPTY);
        ((ServerPlayerEntity)player).networkHandler.sendPacket(new ScreenHandlerSlotUpdateS2CPacket(handler.syncId, handler.nextRevision(), 0, ItemStack.EMPTY));
        ci.cancel();
    }
}
