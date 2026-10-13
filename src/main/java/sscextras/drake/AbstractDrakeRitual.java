package sscextras.drake;

import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import sscextras.rituals.RitualComposition;
import sscextras.rituals.RitualStage;
import sscextras.rituals.RitualStageRunner;
import sscextras.rituals.AbstractRitual;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class AbstractDrakeRitual extends AbstractRitual<AbstractDrakeRitual.Context> {
    public record Context(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        public PillagerEntity actor(int index) {
            return (PillagerEntity)player.getServerWorld().getEntity(claim.attendants.get(index));
        }
    }
    private static final RitualComposition THREE_ENFORCERS = RitualComposition.threeEnforcers();
    protected final DrakeRitualCheckpoint checkpoint;
    private UUID target;

    protected AbstractDrakeRitual(DrakeRitualCheckpoint checkpoint, List<RitualStage<Context>> stages) {
        super(stages, checkpoint.stages);
        this.checkpoint = checkpoint;
    }
    public RitualComposition composition() { return THREE_ENFORCERS; }
    public UUID target() { return target; }
    public int ticks() { return stages.timeline(); }
    public int targetRole() { return DrakeSoulbinding.RESTRAINED; }
    public int paw() { return -1; }
    public String soulAnimation() { return ""; }
    public int presentationTicks() { return 0; }
    public boolean growsBody() { return false; }
    public boolean holds(int index) { return !composition().participants().get(index).slot().equals("specialist"); }
    public abstract String hintPrefix();
    public abstract void complete(Context context);
    public abstract int actorRole(Context context, int index);
    public ItemStack displayedItem(Context context, int index) { return ItemStack.EMPTY; }
    protected boolean prepare(Context context) { return true; }
    public void tickAction(Context context) { }
    public void interruptAction() { checkpoint.feedingTicks = 0; pause(); }

    public boolean assign(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (status() == Status.FINISHED || claim.attendants.size() != composition().size()) return false;
        var owners = DrakeOutpostOwnership.get(player.getServer()).assignments();
        var participants = new ArrayList<>(claim.attendants); participants.add(player.getUuid());
        if (!reserve(owners, participants)) return false;
        target = player.getUuid(); stages.resume();
        return true;
    }
    public boolean replace(UUID previous, UUID replacement) {
        return replaceParticipant(previous, replacement);
    }

    public Vec3d position(DrakeOutpostOwnership.Claim claim, int index) {
        return composition().position(DrakeSoulbinding.hayPosition(claim), DrakeStableLayout.inward(claim.stable, claim.gate()), index);
    }

    public RitualStageRunner.Result advance(Context context, boolean ready) {
        if (!ready) interruptAction(); else resume();
        int previous = ticks();
        boolean work = ready && prepare(context);
        var result = stages.tick(context, 20, work);
        if (ready) afterAdvance(context, previous);
        DrakeOutpostOwnership.get(context.player().getServer()).markDirty();
        return result;
    }
    protected void afterAdvance(Context context, int previousTicks) { }
}
