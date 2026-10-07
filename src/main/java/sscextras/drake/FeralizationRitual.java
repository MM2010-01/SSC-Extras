package sscextras.drake;

import net.minecraft.item.ItemStack;

public final class FeralizationRitual extends SoulRitual {
    public FeralizationRitual(DrakeRitualCheckpoint checkpoint) { super(checkpoint, 3); }
    @Override public String hintPrefix() { return "punishment"; }
    @Override protected String feederSlot() { return "guide"; }
    @Override protected ItemStack catalyst() { return new ItemStack(BeastizationCatalyst.ITEM); }
    @Override protected boolean feedBeforeProgress(Context context) {
        boolean finishBody = ticks() >= 300 && EarthenDrake.stage(context.player()) < 3;
        int required = ticks() >= 300 ? Math.max(9, checkpoint.catalystsFed + (finishBody ? 1 : 0)) : Math.min(4, ticks() / 60);
        if (checkpoint.catalystsFed < required) {
            if (!feed(context)) return false;
            if (checkpoint.catalystsFed < required || finishBody) { feed(context); return false; }
        }
        return true;
    }
    @Override public void complete(Context context) { DrakeSoulbinding.completeSoul(context.player(), context.claim(), true); }
}
