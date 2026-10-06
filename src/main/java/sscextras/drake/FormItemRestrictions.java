package sscextras.drake;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PreventItemUsePower;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

public final class FormItemRestrictions {
    private FormItemRestrictions() { }

    public static boolean prevents(PlayerEntity player, ItemStack stack) {
        return !stack.isEmpty() && !player.isCreative() && !player.isSpectator()
                && PowerHolderComponent.getPowers(player, PreventItemUsePower.class).stream().anyMatch(power -> power.doesPrevent(stack));
    }

    public static boolean rejectHeldTool(PlayerEntity player) {
        // Read the physical slot, so native claws and infused tools retain their own behavior.
        var inventory = player.getInventory();
        var stack = inventory.main.get(inventory.selectedSlot);
        if (stack.isFood() || !prevents(player, stack)) return false;
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) serverPlayer.dropSelectedItem(true);
        return true;
    }
}
