package sscextras.effigy;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;

public final class EffigyScreenHandler extends ScreenHandler {
    public static final int INFUSION_COUNT = InfusionSlot.values().length;
    private final PlayerEntity player;
    private final InfusionInventory infusions;
    private final ScreenHandlerContext context;
    private final Property visibleMask = Property.create();
    private final Property inactiveMask = Property.create();

    public EffigyScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId, playerInventory, ScreenHandlerContext.EMPTY);
    }

    public EffigyScreenHandler(int syncId, PlayerInventory playerInventory, ScreenHandlerContext context) {
        super(FeralEffigy.SCREEN, syncId);
        player = playerInventory.player;
        infusions = Infusions.inventory(player);
        this.context = context;
        addProperty(visibleMask);
        addProperty(inactiveMask);
        updateMask();
        for (InfusionSlot kind : InfusionSlot.values()) addSlot(new Slot(infusions, kind.ordinal(), kind.x, kind.y) {
            @Override public boolean canInsert(ItemStack stack) {
                return Infusions.restricted(player, kind, stack);
            }
            @Override public int getMaxItemCount() { return 1; }
            @Override public boolean isEnabled() { return visible(kind); }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 125 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, 183));
    }

    public boolean visible(InfusionSlot slot) { return (visibleMask.get() & (1 << slot.ordinal())) != 0; }
    public boolean inactive(InfusionSlot slot) { return (inactiveMask.get() & (1 << slot.ordinal())) != 0; }
    public boolean anyVisible() { return visibleMask.get() != 0; }

    private void updateMask() {
        if (player.getWorld().isClient) return;
        int mask = 0, inactive = 0;
        for (InfusionSlot slot : InfusionSlot.values()) {
            if (!infusions.getStack(slot.ordinal()).isEmpty()) {
                mask |= 1 << slot.ordinal();
                if (Infusions.active(player, slot).isEmpty()) inactive |= 1 << slot.ordinal();
            } else if (Infusions.available(player, slot)) mask |= 1 << slot.ordinal();
        }
        visibleMask.set(mask);
        inactiveMask.set(inactive);
    }

    @Override public void sendContentUpdates() {
        updateMask();
        super.sendContentUpdates();
        infusions.refreshAttributes();
        infusions.flush();
    }

    @Override public boolean canUse(PlayerEntity player) { return infusions.canPlayerUse(player) && canUse(context, player, FeralEffigy.BLOCK); }

    @Override public ItemStack quickMove(PlayerEntity player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (!source.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = source.getStack(), original = stack.copy();
        if (index < INFUSION_COUNT) {
            if (!insertItem(stack, INFUSION_COUNT, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean inserted = false;
            for (int i = 0; i < INFUSION_COUNT; i++) {
                if (slots.get(i).canInsert(stack) && insertItem(stack, i, i + 1, false)) {
                    inserted = true;
                    break;
                }
            }
            if (!inserted) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) source.setStack(ItemStack.EMPTY); else source.markDirty();
        source.onTakeItem(player, stack);
        Infusions.changed(player);
        return original;
    }

    @Override public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        Infusions.changed(player);
    }
}
