package sscextras.drake;

import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import sscextras.rituals.RitualStage;
import java.util.ArrayList;
import java.util.List;

public final class ShoeFittingRitual extends AbstractRitual {
    public ShoeFittingRitual(DrakeRitualCheckpoint checkpoint) { super(checkpoint, sequence()); }
    private static List<RitualStage<Context>> sequence() {
        var stages = new ArrayList<RitualStage<Context>>();
        for (int i = 0; i < 4; i++) {
            int paw = i;
            stages.add(RitualStage.afterAndWhen("fit_paw_" + paw, DrakeShoes.PAW_TICKS, c -> true,
                    c -> paw != 1 && paw != 3 || DrakeShoes.equipPair(c.player(), paw == 3)));
        }
        return List.copyOf(stages);
    }
    @Override public String hintPrefix() { return "shoeing"; }
    @Override public int targetRole() { return DrakeSoulbinding.SHOE_RESTRAINED; }
    @Override public int presentationTicks() { return 0; }
    @Override public boolean growsBody() { return false; }
    @Override public int paw() { return Math.min(3, ticks() / DrakeShoes.PAW_TICKS); }
    @Override public int actorRole(Context context, int index) {
        return holds(index) ? DrakeSoulbinding.HOLDING : DrakeSoulbinding.SHOEING;
    }
    @Override public ItemStack displayedItem(Context context, int index) {
        return holds(index) ? ItemStack.EMPTY : DrakeEquipment.SHOES.getDefaultStack();
    }
    @Override public Vec3d position(DrakeOutpostOwnership.Claim claim, int index) {
        if (holds(index)) return super.position(claim, index);
        int inward = DrakeStableLayout.inward(claim.stable, claim.gate());
        boolean quadruped = claim.shoeingStage == 3;
        double side = (paw() % 2 == 0 ? -.85 : .85) * (quadruped ? -1 : 1);
        double end = quadruped ? paw() < 2 ? -.4 : .4 : paw() < 2 ? .5 : -.5;
        return DrakeSoulbinding.hayPosition(claim).add(side * inward, 0, end * inward);
    }
    @Override protected boolean prepare(Context context) {
        DrakeSoulbinding.shoeingPaw(context.player(), paw());
        for (int i = 0; i < composition().size(); i++) DrakeSoulbinding.role(context.actor(i), actorRole(context, i));
        if (checkpoint.stages.elapsed() == 0) DrakeSoulbinding.hint(context.player(), "shoeing_paw_" + paw());
        var worker = context.actor(composition().index("specialist"));
        DrakeSoulbinding.shoeingPaw(worker, paw()); worker.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        context.player().getWorld().playSound(null, context.player().getBlockPos(), SoundEvents.BLOCK_ANVIL_USE,
                SoundCategory.HOSTILE, .35f, 1.25f);
        return true;
    }
    @Override protected void afterAdvance(Context context, int previous) { DrakeSoulbinding.shoeingPaw(context.player(), paw()); }
    @Override public void complete(Context context) {
        context.claim().shoeingDue = false; context.claim().shoeingViolations = 0;
        context.player().clearActiveItem();
        DrakeSoulbinding.finishRitual(context.player(), context.claim());
        DrakeSoulbinding.hint(context.player(), "shoeing_complete");
    }
}
