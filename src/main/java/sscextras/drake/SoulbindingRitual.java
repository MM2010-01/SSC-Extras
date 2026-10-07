package sscextras.drake;

import net.minecraft.item.ItemStack;

public final class SoulbindingRitual extends SoulRitual {
    public SoulbindingRitual(DrakeRitualCheckpoint checkpoint) { super(checkpoint, 2); }
    @Override public String hintPrefix() { return "ritual"; }
    @Override protected String feederSlot() { return "specialist"; }
    @Override protected ItemStack catalyst() { return new ItemStack(net.onixary.shapeShifterCurseFabric.items.RegCustomItem.CATALYST); }
    @Override protected boolean feedBeforeProgress(Context context) {
        if (EarthenDrake.stage(context.player()) < 2 && ticks() <= 300 || checkpoint.feedingTicks > 0) {
            if (feed(context) && EarthenDrake.stage(context.player()) < 2) feed(context);
        } else checkpoint.feedingTicks = 0;
        return true;
    }
    @Override public void complete(Context context) { DrakeSoulbinding.completeSoul(context.player(), context.claim(), false); }
}
