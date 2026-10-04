package sscextras.drake;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.Trinket;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;

final class VanillaSaddleTrinket implements Trinket {
    static void register() { TrinketsApi.registerTrinket(Items.SADDLE, new VanillaSaddleTrinket()); }

    private static AccessoryItem.SlotData slot(SlotReference reference) {
        var type = reference.inventory().getSlotType();
        return new AccessoryItem.SlotData(new Identifier("trinkets", type.getGroup() + "/" + type.getName()), reference.index());
    }

    @Override public boolean canEquip(ItemStack stack, SlotReference reference, LivingEntity entity) {
        return DrakeEquipment.SADDLE.canEquip(stack, entity, slot(reference));
    }

    @Override public boolean canUnequip(ItemStack stack, SlotReference reference, LivingEntity entity) {
        return DrakeEquipment.SADDLE.canUnequip(stack, entity, slot(reference));
    }

    @Override public void onEquip(ItemStack stack, SlotReference reference, LivingEntity entity) {
        DrakeEquipment.SADDLE.onEquip(stack, entity, slot(reference));
    }

    @Override public void onUnequip(ItemStack stack, SlotReference reference, LivingEntity entity) {
        DrakeEquipment.SADDLE.onUnequip(stack, entity, slot(reference));
    }

    @Override public void tick(ItemStack stack, SlotReference reference, LivingEntity entity) {
        DrakeEquipment.SADDLE.accessoryTick(stack, entity, slot(reference));
    }
}
