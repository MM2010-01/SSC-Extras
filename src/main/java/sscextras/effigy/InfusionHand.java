package sscextras.effigy;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** Exposes an infused stack only while Minecraft executes its normal item action. */
public final class InfusionHand implements AutoCloseable {
    private static final ThreadLocal<InfusionHand> CURRENT = new ThreadLocal<>();
    private final PlayerEntity player;
    private final ItemStack stack;
    private final InfusionHand previous;

    public InfusionHand(PlayerEntity player, ItemStack stack) {
        this.player = player;
        this.stack = stack;
        previous = CURRENT.get();
        CURRENT.set(this);
    }

    public static ItemStack get(PlayerEntity player) {
        InfusionHand context = CURRENT.get();
        return context != null && context.player == player ? context.stack : ItemStack.EMPTY;
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
