package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.SoulboundEquipment;
import java.util.LinkedHashMap;
import java.util.Map;

@Mixin(PlayerEntity.class)
public abstract class SoulboundInventoryMixin {
    @Unique private final Map<Integer, ItemStack> sscExtras$keptGear = new LinkedHashMap<>();

    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void sscExtras$keepGear(CallbackInfo ci) {
        var inventory = ((PlayerEntity)(Object)this).getInventory();
        for (int i = 0; i < inventory.size(); i++) if (SoulboundEquipment.bound(inventory.getStack(i))) {
            sscExtras$keptGear.put(i, inventory.getStack(i));
            inventory.setStack(i, ItemStack.EMPTY);
        }
    }

    @Inject(method = "dropInventory", at = @At("RETURN"))
    private void sscExtras$restoreGear(CallbackInfo ci) {
        var inventory = ((PlayerEntity)(Object)this).getInventory();
        sscExtras$keptGear.forEach(inventory::setStack);
        sscExtras$keptGear.clear();
    }
}
