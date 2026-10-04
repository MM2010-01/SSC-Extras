package sscextras.collar;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import sscextras.drake.DrakeAccessoryItem;
import sscextras.drake.DrakeEquipment;
import sscextras.drake.DrakeLeashing;

public final class TamingCollar {
    private TamingCollar() { }

    public static boolean worn(PlayerEntity player) {
        return CollarSlots.get(player).stream().anyMatch(slot -> slot.stack().isOf(Collars.TAMING));
    }

    public static boolean restricted(PlayerEntity player) {
        return !player.isCreative() && !player.isSpectator() && worn(player);
    }

    public static boolean equip(PlayerEntity player) {
        if (player.getWorld().isClient || !player.isAlive() || player.isCreative() || player.isSpectator()) return false;
        var slots = CollarSlots.get(player);
        if (slots.isEmpty()) return false;
        if (!worn(player)) {
            var destination = slots.get(0);
            for (var slot : CollarSlots.includingLegacy(player)) {
                ItemStack displaced = slot.get(player);
                if (displaced.isEmpty()) continue;
                slot.set(player, ItemStack.EMPTY);
                if (slot.get(player).isEmpty()) player.getInventory().offerOrDrop(displaced);
            }
            if (!destination.get(player).isEmpty()) return false;
            var stack = Collars.TAMING.getDefaultStack();
            name(player, stack);
            destination.set(player, stack);
            if (!destination.get(player).isOf(Collars.TAMING)) return false;
            Collars.TAMING.onEquip(destination.get(player), player, new AccessoryItem.SlotData(new net.minecraft.util.Identifier(
                    CuriosCompat.instance == null ? "trinkets" : "curios",
                    CuriosCompat.instance == null ? "chest/necklace" : "necklace"), destination.index()));
            player.sendMessage(Text.translatable("message.ssc-extras.drake.taming_collar").formatted(Formatting.DARK_PURPLE), false);
        }
        restore(player, DrakeEquipment.REINS);
        restore(player, DrakeEquipment.SADDLE);
        sscextras.drake.BlindingRein.upgrade(player);
        player.getInventory().markDirty();
        player.currentScreenHandler.sendContentUpdates();
        return true;
    }

    public static void name(PlayerEntity player, ItemStack stack) {
        var claim = sscextras.drake.DrakeOutpostOwnership.claim(player);
        if (claim == null) return;
        var name = Text.translatable("item.ssc-extras.named_cursed_taming_collar", claim.name);
        if (!stack.hasCustomName() || !stack.getName().equals(name)) stack.setCustomName(name);
    }

    private static void restore(PlayerEntity player, DrakeAccessoryItem item) {
        if (!DrakeEquipment.equipped(player, item).isEmpty()) return;
        var io = DrakeEquipment.slots();
        if (io == null) return;
        var stacks = DrakeEquipment.stacks(player, item);
        if (stacks.isEmpty()) return;
        if (stacks.stream().noneMatch(ItemStack::isEmpty)) {
            String group = CuriosCompat.instance == null ? item.group : "";
            String name = CuriosCompat.instance == null ? item.slot : item.curiosSlot();
            var displaced = stacks.get(0);
            io.setEntitySlot(player, group, name, 0, ItemStack.EMPTY);
            if (io.getEntitySlot(player, group, name, 0).isEmpty()) player.getInventory().offerOrDrop(displaced);
        }
        DrakeEquipment.tryEquip(player, new ItemStack(item), false);
    }

    public static ActionResult struggle(PlayerEntity player, Hand hand) {
        if (!player.getWorld().isClient && hand == Hand.MAIN_HAND && player.getRandom().nextFloat() < .05f) {
            DrakeLeashing.detach(player, true);
            player.sendMessage(Text.translatable("message.ssc-extras.drake.struggle_free").formatted(Formatting.YELLOW), false);
        }
        return ActionResult.SUCCESS;
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            var block = world.getBlockState(hit.getBlockPos());
            return restricted(player) && block.getBlock() instanceof FenceGateBlock && !block.get(FenceGateBlock.OPEN)
                    ? ActionResult.FAIL : ActionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> restricted(player) ? ActionResult.FAIL : ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> !restricted(player));
        AttackEntityCallback.EVENT.register((player, world, hand, target, hit) ->
                restricted(player) && DrakeLeashing.holder(player) == target ? ActionResult.FAIL : ActionResult.PASS);
    }
}
