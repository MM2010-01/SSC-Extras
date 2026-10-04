package sscextras.drake;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import sscextras.collar.CuriosCompat;
import sscextras.collar.TamingCollar;

public final class BlindingRein {
    private static final String CLOSED = "SscExtrasBlinkersClosed";

    private BlindingRein() { }

    public static boolean closed(ItemStack stack) {
        return stack.isOf(DrakeEquipment.BLINDING_REIN) && stack.hasNbt() && stack.getNbt().getBoolean(CLOSED);
    }

    public static void setClosed(ItemStack stack, boolean closed) {
        if (!stack.isOf(DrakeEquipment.BLINDING_REIN)) return;
        if (closed) stack.getOrCreateNbt().putBoolean(CLOSED, true);
        else if (stack.hasNbt()) stack.getNbt().remove(CLOSED);
    }

    public static void setClosed(PlayerEntity player, boolean closed) {
        if (player.getWorld().isClient) return;
        for (var stack : DrakeEquipment.stacks(player, DrakeEquipment.BLINDING_REIN)) {
            if (stack.isOf(DrakeEquipment.BLINDING_REIN) && closed(stack) != closed) setClosed(stack, closed);
        }
    }

    public static boolean needsUpgrade(PlayerEntity player) {
        return TamingCollar.worn(player) && DrakeEquipment.equipped(player, DrakeEquipment.REINS).isOf(DrakeEquipment.REINS);
    }

    public static boolean upgrade(PlayerEntity player) {
        if (player.getWorld().isClient || !player.isAlive() || player.isCreative() || player.isSpectator() || !needsUpgrade(player)) return false;
        var item = DrakeEquipment.BLINDING_REIN;
        var io = DrakeEquipment.slots();
        var stacks = DrakeEquipment.stacks(player, item);
        for (int index = 0; index < stacks.size(); index++) {
            var old = stacks.get(index);
            if (!old.isOf(DrakeEquipment.REINS)) continue;
            var replacement = new ItemStack(item);
            if (old.hasNbt()) replacement.setNbt(old.getNbt().copy());
            setClosed(replacement, false);
            boolean curios = CuriosCompat.instance != null;
            String group = curios ? "" : item.group, slot = curios ? item.curiosSlot() : item.slot;
            io.setEntitySlot(player, group, slot, index, replacement);
            var equipped = io.getEntitySlot(player, group, slot, index);
            if (!equipped.isOf(item)) return false;
            item.onEquip(equipped, player, new AccessoryItem.SlotData(new Identifier(
                    curios ? "curios" : "trinkets", curios ? slot : group + "/" + slot), index));
            player.currentScreenHandler.sendContentUpdates();
            player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_HORSE_SADDLE, SoundCategory.PLAYERS, 1, 1);
            return true;
        }
        return false;
    }
}
