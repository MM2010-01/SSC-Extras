package sscextras.drake;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import sscextras.events.AssignedNpcEvent;
import java.util.EnumSet;
import java.util.List;

/** A native goal drives one reserved inspection/physical delivery, resuming saved work after interruption. */
public final class DrakeConfiscationGoal extends Goal {
    private final PillagerEntity guard;
    private ServerPlayerEntity player;
    private DrakeOutpostOwnership.Claim claim;
    private DrakeConfiscationStorage storage;
    private DrakeConfiscationStorage.Destination destination;
    private Run run;
    private int nextSearch, nextPath, started, biteTicks;
    private boolean inspecting;

    private final class Run extends AssignedNpcEvent {
        boolean assigned;
        boolean acquire() {
            var actors = new java.util.ArrayList<>(List.of(guard.getUuid(), storage.reservation));
            if (player != null) actors.add(player.getUuid());
            return assigned = reserve(DrakeOutpostOwnership.get(guard.getServer()).assignments(), actors);
        }
        void leavePlayer() { if (player != null) releaseParticipant(player.getUuid()); }
        @Override protected void releaseParticipants() {
            if (!assigned) return;
            guard.getNavigation().stop();
            ((DrakeFaction.EquipmentDisplay)guard).sscExtras$showEquipment(ItemStack.EMPTY);
            if (DrakeSoulbinding.role(guard) == DrakeSoulbinding.FEEDING) DrakeSoulbinding.role(guard, 0);
        }
    }

    static void unloaded(net.minecraft.entity.Entity entity) {
        if (entity.getServer() == null) return;
        var owner = DrakeOutpostOwnership.get(entity.getServer()).assignments().owner(entity.getUuid());
        if (owner instanceof DrakeConfiscationGoal.Run run) run.finish();
    }

    public DrakeConfiscationGoal(PillagerEntity guard) {
        this.guard = guard;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    private boolean available() {
        return guard.isAlive() && !guard.isRemoved() && !guard.isAiDisabled() && !guard.hasVehicle()
                && !guard.hasActiveRaid() && !DrakeFaction.fighting(guard)
                && !(guard instanceof DrakeVisitorEntity) && !MountMerchants.trader(guard)
                && DrakeSoulbinding.attendee(guard) == null;
    }

    private boolean eligible(ServerPlayerEntity candidate, DrakeOutpostOwnership.Claim owned) {
        return owned != null && candidate.isAlive() && !candidate.isRemoved() && !candidate.isDisconnected()
                && !candidate.isCreative() && !candidate.isSpectator() && !candidate.isSleeping()
                && candidate.getWorld() == guard.getWorld() && owned.world.equals(guard.getWorld().getRegistryKey())
                && !candidate.hasVehicle() && !candidate.hasPassengers() && !owned.awaitingRespawn
                && !owned.tryingToEscape && !DrakeSoulbinding.ritualActive(candidate)
                && !DrakeBattleGoal.assigned(candidate) && !MountMarket.get(candidate.getServer()).busy(candidate)
                && !BondOfTheBeastCompat.hasOwner(candidate) && !MountMarket.playerOwned(candidate)
                && owned.stall().expand(3.0 / 8, 0, 3.0 / 8).contains(candidate.getPos());
    }

    @Override public boolean canStart() {
        if (guard.age < nextSearch || !available()) return false;
        nextSearch = guard.age + 100;
        var data = DrakeOutpostOwnership.get(guard.getServer());
        if (data.assignments().owner(guard.getUuid()) != null) return false;
        var stable = ((DrakeStableNavigation)guard.getNavigation()).stable();
        if (stable == null) return false;
        storage = data.confiscationStorage((ServerWorld)guard.getWorld(), stable.getBoundingBox());
        if (data.assignments().owner(storage.reservation) != null || !storage.resolveBuilding((ServerWorld)guard.getWorld())) return false;
        player = null; claim = null;
        if (!storage.cargo.isEmpty()) return true;
        for (var candidate : ((ServerWorld)guard.getWorld()).getPlayers()) {
            var owned = DrakeOutpostOwnership.claim(candidate);
            if (eligible(candidate, owned) && owned.matches(candidate.getWorld(), stable)
                    && (!owned.confiscation.initialDone || !owned.soulbound && owned.confiscation.checkDue)
                    && data.assignments().owner(candidate.getUuid()) == null) {
                player = candidate; claim = owned; return true;
            }
        }
        return false;
    }

    @Override public void start() {
        run = new Run();
        if (!run.acquire()) { run.finish(); return; }
        run.resume(); started = guard.age; nextPath = biteTicks = 0; destination = null;
        inspecting = player != null;
        guard.clearActiveItem(); guard.setCharging(false); guard.setTarget(null);
    }

    @Override public boolean shouldContinue() {
        if (run == null || run.status() == sscextras.events.NpcEvent.Status.FINISHED
                || !available() || guard.age - started > 1200) return false;
        return inspecting ? DrakeOutpostOwnership.claim(player) == claim && eligible(player, claim)
                : !storage.cargo.isEmpty();
    }

    @Override public boolean shouldRunEveryTick() { return true; }

    @Override public void tick() {
        if (!shouldContinue()) return;
        if (inspecting) { inspect(); return; }
        if (destination == null) destination = storage.destination(guard);
        if (destination == null) { run.finish(); return; }
        var position = Vec3d.ofBottomCenter(destination.stand());
        ((DrakeFaction.EquipmentDisplay)guard).sscExtras$showEquipment(new ItemStack(Items.CHEST));
        guard.getLookControl().lookAt(position.x, position.y + 1, position.z);
        if (guard.squaredDistanceTo(position) > 2.25) {
            if (guard.age >= nextPath) {
                nextPath = guard.age + 20;
                guard.getNavigation().startMovingTo(position.x, position.y, position.z, .8);
            }
            return;
        }
        guard.getNavigation().stop();
        storage.deliver(guard, destination);
        destination = null;
        started = guard.age;
    }

    private void inspect() {
        var state = claim.confiscation;
        guard.getLookControl().lookAt(player, 30, 30);
        if (!DrakeFeedGoal.approach(guard, player, claim) || player.isUsingItem()) {
            biteTicks = 0;
            if (DrakeSoulbinding.role(guard) == DrakeSoulbinding.FEEDING) DrakeSoulbinding.role(guard, 0);
            return;
        }
        if (!state.initialDone) {
            if (!state.initialTaken) {
                state.feedingCatalysts = EarthenDrake.stage(player) != 3;
                DrakeDialogue.say(player, "confiscation");
                DrakeConfiscation.take(player, storage, true, state.feedingCatalysts);
                state.initialTaken = true;
                DrakeConfiscation.dirty(player);
            }
            if (state.feedingCatalysts && DrakeConfiscation.hasCatalyst(player)) {
                if (!state.catalystHint) {
                    DrakeDialogue.say(player, "confiscation_catalysts");
                    state.catalystHint = true; DrakeConfiscation.dirty(player);
                }
                var food = RegCustomItem.CATALYST.getDefaultStack();
                ((DrakeFaction.EquipmentDisplay)guard).sscExtras$showEquipment(food);
                DrakeSoulbinding.role(guard, DrakeSoulbinding.FEEDING);
                DrakeFeedGoal.chew(player, food, ++biteTicks);
                if (biteTicks >= DrakeSoulbinding.FEED_TICKS) {
                    DrakeConfiscation.feedCatalyst(player, storage); biteTicks = 0;
                    guard.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    started = guard.age; // A long stack is real progress, not a stuck approach.
                }
                return;
            }
            // Also collect any non-whitelisted items acquired during the feeding sequence.
            DrakeConfiscation.take(player, storage, true, false);
            state.initialDone = true; state.feedingCatalysts = false; state.checkDue = false;
            state.lastDay = Math.max(state.lastDay, Math.floorDiv(player.getWorld().getTimeOfDay(), 24000));
        } else {
            DrakeDialogue.say(player, "contraband_check");
            var found = DrakeConfiscation.take(player, storage, false, false);
            for (var kind : found) DrakeDialogue.say(player, kind.line);
            state.adjust(found.isEmpty() ? -1 : 20);
            state.misbehaved |= !found.isEmpty();
            state.checkDue = false;
        }
        DrakeConfiscation.dirty(player);
        run.leavePlayer(); inspecting = false; player = null; claim = null;
        if (DrakeSoulbinding.role(guard) == DrakeSoulbinding.FEEDING) DrakeSoulbinding.role(guard, 0);
        started = guard.age;
    }

    @Override public void stop() {
        if (run != null) run.finish();
        run = null; player = null; claim = null; storage = null; destination = null;
        inspecting = false; biteTicks = 0; nextSearch = guard.age + 200;
    }
}
