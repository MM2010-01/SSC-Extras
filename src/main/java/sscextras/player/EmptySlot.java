package sscextras.player;

import java.util.List;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** A real inventory item whose held representation is an empty hand. */
public final class EmptySlot extends Item {
    public static final Identifier ID = new Identifier("ssc-extras", "empty_slot");
    public static final EmptySlot ITEM = new EmptySlot();
    private static final ThreadLocal<PlayerEntity> PHYSICAL_HANDS = new ThreadLocal<>();

    private EmptySlot() { super(new Settings().maxCount(1)); }
    public static void register() { Registry.register(Registries.ITEM, ID, ITEM); }
    public static boolean isPlaceholder(ItemStack stack) { return stack.isOf(ITEM); }
    public static boolean isBare(ItemStack stack) { return stack.isEmpty() || isPlaceholder(stack); }
    public static ItemStack held(ItemStack stack) { return isPlaceholder(stack) ? ItemStack.EMPTY : stack; }
    public static boolean bareMainHand(PlayerEntity player) {
        var inventory = player.getInventory();
        return isBare(inventory.main.get(inventory.selectedSlot));
    }

    // Native inventory swaps must see and move the real items, including Forge's swap event.
    public static boolean physicalHands(PlayerEntity player) { return PHYSICAL_HANDS.get() == player; }
    public static void withPhysicalHands(PlayerEntity player, Runnable action) {
        PlayerEntity previous = PHYSICAL_HANDS.get();
        PHYSICAL_HANDS.set(player);
        try { action.run(); }
        finally {
            if (previous == null) PHYSICAL_HANDS.remove(); else PHYSICAL_HANDS.set(previous);
        }
    }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.ssc-extras.empty_slot").formatted(Formatting.GRAY));
    }
}
