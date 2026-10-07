package sscextras.drake;

import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import sscextras.rituals.RitualStage;
import java.util.ArrayList;
import java.util.List;

public abstract class SoulRitual extends AbstractRitual {
    private final int bodyStage;
    private final String animationId;
    private final int returnAt, returnedAt;
    protected SoulRitual(DrakeRitualCheckpoint checkpoint, int bodyStage) {
        this(checkpoint, bodyStage, DrakeSoulAnimations.TRANSFORMATION_ID);
    }
    protected SoulRitual(DrakeRitualCheckpoint checkpoint, int bodyStage, String animationId) {
        super(checkpoint, sequence(bodyStage, animationId)); this.bodyStage = bodyStage;
        this.animationId = animationId;
        var animation = DrakeSoulAnimations.get(animationId);
        returnedAt = animation.duration();
        returnAt = returnedAt - animation.stages().get(animation.stages().size() - 1).duration();
    }
    private static boolean bodyReady(Context context, int stage) {
        return EarthenDrake.stage(context.player()) >= stage && !TransformManager.getPlayerTransformData(context.player()).isTransforming;
    }
    private static List<RitualStage<Context>> sequence(int bodyStage, String animationId) {
        var result = new ArrayList<RitualStage<Context>>();
        var animation = DrakeSoulAnimations.get(animationId);
        if (animation == null || animation.stages().size() < 2
                || animation.stages().get(animation.stages().size() - 1).motion() != sscextras.rituals.SoulAnimationStage.Motion.RETURN)
            throw new IllegalArgumentException("Soul ritual requires an animation ending in return-to-body");
        var bodyGate = animation.stages().get(animation.stages().size() - 2);
        for (var clip : animation.stages())
            result.add(RitualStage.afterAndWhen(clip.id(), clip.duration(),
                    c -> clip != bodyGate || bodyReady(c, bodyStage), c -> true));
        result.add(RitualStage.afterAndWhen("finish_chant", 240, c -> bodyReady(c, bodyStage), c -> true));
        return List.copyOf(result);
    }
    protected abstract String feederSlot();
    @Override public String soulAnimation() { return animationId; }
    @Override public int presentationTicks() { return ticks(); }
    @Override public boolean growsBody() { return ticks() > 0 && ticks() <= returnAt; }
    protected abstract ItemStack catalyst();
    protected abstract boolean feedBeforeProgress(Context context);
    protected int feeder() { return composition().index(feederSlot()); }
    @Override public boolean holds(int index) { return index != feeder(); }
    @Override public int actorRole(Context context, int index) {
        return holds(index) ? DrakeSoulbinding.HOLDING : checkpoint.feedingTicks > 0 ? DrakeSoulbinding.FEEDING : DrakeSoulbinding.CHANTING;
    }
    @Override public ItemStack displayedItem(Context context, int index) {
        return index == feeder() && checkpoint.feedingTicks > 0 ? catalyst() : ItemStack.EMPTY;
    }
    @Override protected boolean prepare(Context context) {
        if (ticks() == 0 && TransformManager.getPlayerTransformData(context.player()).isTransforming) return false;
        if (ticks() == 0 && checkpoint.stages.elapsed() == 0) {
            DrakeSoulbinding.hint(context.player(), hintPrefix() + "_begin");
            if (EarthenDrake.stage(context.player()) < 2) DrakeSoulbinding.hint(context.player(), "ritual_change");
        }
        boolean advance = feedBeforeProgress(context);
        for (int i = 0; i < composition().size(); i++) DrakeSoulbinding.role(context.actor(i), actorRole(context, i));
        if (advance && ticks() % 80 == 0) context.player().getWorld().playSound(null, context.player().getBlockPos(),
                SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, .65f, .7f);
        return advance;
    }
    protected boolean feed(Context context) {
        if (checkpoint.feedingTicks == -1) { checkpoint.feedingTicks = 0; return true; }
        if (checkpoint.feedingTicks == 0) {
            if (checkpoint.catalystsFed == 0) DrakeSoulbinding.hint(context.player(), hintPrefix() + "_feed");
            checkpoint.feedingTicks = DrakeSoulbinding.FEED_TICKS;
        }
        return false;
    }
    @Override public void tickAction(Context context) {
        if (checkpoint.feedingTicks <= 0 || status() != Status.RUNNING) return;
        var leader = context.actor(feeder());
        if (DrakeSoulbinding.role(leader) != DrakeSoulbinding.FEEDING) return;
        var food = catalyst();
        DrakeFeedGoal.chew(context.player(), food, DrakeSoulbinding.FEED_TICKS - checkpoint.feedingTicks);
        if (--checkpoint.feedingTicks != 0) return;
        checkpoint.feedingTicks = -1;
        checkpoint.catalystsFed++;
        food.finishUsing(context.player().getWorld(), context.player());
        leader.swingHand(net.minecraft.util.Hand.MAIN_HAND);
        DrakeOutpostOwnership.get(context.player().getServer()).markDirty();
    }
    @Override protected void afterAdvance(Context context, int previous) {
        if (previous < returnAt && ticks() >= returnAt) DrakeSoulbinding.hint(context.player(), bodyStage == 3 ? "punishment_mind" : "ritual_soul");
        if (previous < returnedAt && ticks() >= returnedAt) DrakeSoulbinding.hint(context.player(), bodyStage == 3 ? "punishment_mind_returned" : "ritual_soul_returned");
    }
}
