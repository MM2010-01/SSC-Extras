package sscextras.mixin;

import dev.emi.trinkets.api.TrinketInventory;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.onixary.shapeShifterCurseFabric.util.Accessory.DefaultAccessory$1", remap = false)
public abstract class TrinketsMissingSlotMixin {
    // Accessories can expose a slot type without an inventory for this wearer.
    @Redirect(method = {"getEntitySlots", "getEntitySlot"}, at = @At(value = "INVOKE",
            target = "Ldev/emi/trinkets/api/TrinketInventory;size()I", remap = true))
    private int sscExtras$availableSlots(TrinketInventory inventory) {
        return inventory == null ? 0 : inventory.size();
    }

    @Redirect(method = "setEntitySlot", at = @At(value = "INVOKE",
            target = "Ldev/emi/trinkets/api/TrinketInventory;setStack(ILnet/minecraft/item/ItemStack;)V", remap = true))
    private void sscExtras$setAvailableSlot(TrinketInventory inventory, int index, ItemStack stack) {
        if (inventory != null) inventory.setStack(index, stack);
    }
}
