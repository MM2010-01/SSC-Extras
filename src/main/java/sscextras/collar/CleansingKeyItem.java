package sscextras.collar;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import sscextras.drake.DrakeAccessoryItem;
import sscextras.drake.DrakeEquipment;
import java.util.List;

public final class CleansingKeyItem extends Item {
    public CleansingKeyItem() { super(new Settings().maxDamage(6)); }

    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack key = player.getStackInHand(hand);
        if (!player.isAlive() || player.isSpectator()) return TypedActionResult.pass(key);
        if (world.isClient) return TypedActionResult.success(key);
        return cleanse((ServerPlayerEntity) player, key, hand) == 0
                ? TypedActionResult.pass(key) : TypedActionResult.success(key);
    }

    public static int purgeCursedEquipment(ServerPlayerEntity player) {
        return cleanse(player, null, null);
    }

    private static int cleanse(ServerPlayerEntity player, ItemStack key, Hand hand) {
        int removed = 0;
        for (var slot : CollarSlots.includingLegacy(player)) {
            if (release(player, key, hand, slot.io(), slot.group(), "necklace", slot.index())) removed++;
        }
        var io = DrakeEquipment.slots();
        if (io != null) for (DrakeAccessoryItem item : new DrakeAccessoryItem[]{DrakeEquipment.REINS, DrakeEquipment.SADDLE}) {
            String group = CuriosCompat.instance == null ? item.group : "";
            String name = CuriosCompat.instance == null ? item.slot : item.curiosSlot();
            var stacks = io.getEntitySlot(player, group, name);
            if (stacks != null) for (int i = 0; i < stacks.size(); i++) {
                if (release(player, key, hand, io, group, name, i)) removed++;
            }
        }
        if (io != null) for (boolean feet : new boolean[]{false, true}) {
            String group = CuriosCompat.instance == null ? feet ? "feet" : "hand" : "";
            String name = CuriosCompat.instance == null ? feet ? "shoes" : "glove" : feet ? "feet" : "hands";
            var stacks = sscextras.drake.DrakeShoes.stacks(player, feet);
            for (int i = 0; i < stacks.size(); i++) if (stacks.get(i).isOf(DrakeEquipment.SHOES))
                if (release(player, key, hand, io, group, name, i)) removed++;
        }
        if (removed == 0) return 0;
        player.addStatusEffect(new StatusEffectInstance(Collars.CURSE_CLEANSED, 600, 0, false, false, true));
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, player.getSoundCategory(), .6f, 1.4f);
        return removed;
    }

    private static boolean release(ServerPlayerEntity player, ItemStack key, Hand hand,
            AccessoryUtils.AccessoryIO io, String group, String name, int index) {
        if (key != null && (key.isEmpty() || key.getDamage() >= key.getMaxDamage())) return false;
        ItemStack stack = io.getEntitySlot(player, group, name, index);
        if (stack == null || !(stack.isOf(Collars.CURSED) || stack.isOf(Collars.TAMING) || DrakeEquipment.isReins(stack)
                || stack.isOf(DrakeEquipment.SADDLE) || stack.isOf(DrakeEquipment.SHOES))) return false;
        ItemStack dropped = stack.copy();
        io.setEntitySlot(player, group, name, index, ItemStack.EMPTY);
        if (!io.getEntitySlot(player, group, name, index).isEmpty()) return false;
        DrakeEquipment.clearSetBinding(dropped);
        DrakeEquipment.refreshBinding(player);
        player.dropItem(dropped, false);
        if (key != null) {
            key.setDamage(key.getDamage() + 1);
            if (key.getDamage() >= key.getMaxDamage()) {
                player.sendToolBreakStatus(hand);
                key.decrement(1);
            }
        }
        return true;
    }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> lines, TooltipContext context) {
        lines.add(Text.translatable("tooltip.ssc-extras.cleansing_key.release").formatted(Formatting.GRAY));
        lines.add(Text.translatable("tooltip.ssc-extras.cleansing_key.cleansed").formatted(Formatting.GRAY));
        lines.add(Text.translatable("tooltip.ssc-extras.cleansing_key.uses", stack.getMaxDamage() - stack.getDamage())
                .formatted(Formatting.GRAY));
    }
}
