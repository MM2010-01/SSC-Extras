package sscextras.drake;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.EnumSet;

public final class DrakeRitualGoal extends Goal {
    private final PillagerEntity pillager;
    private ServerPlayerEntity player;
    private int nextPath;
    private boolean atGate;
    private BlockPos sourceGate;

    public DrakeRitualGoal(PillagerEntity pillager) {
        this.pillager = pillager;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override public boolean canStart() {
        player = DrakeSoulbinding.attendee(pillager);
        return player != null && DrakeSoulbinding.available(pillager);
    }

    @Override public boolean shouldContinue() {
        return player != null && player.isAlive() && player == DrakeSoulbinding.attendee(pillager) && DrakeSoulbinding.available(pillager);
    }

    @Override public void start() {
        nextPath = 0; atGate = false; sourceGate = null;
        pillager.setTarget(null); pillager.clearActiveItem(); pillager.setCharging(false);
    }

    @Override public boolean shouldRunEveryTick() { return true; }

    @Override public void tick() {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null || claim.ritualRun == null) return;
        var run = claim.ritualRun;
        int index = claim.attendants.indexOf(pillager.getUuid());
        if (index < 0) return;
        var navigation = (DrakeStableNavigation)pillager.getNavigation();
        var body = pillager.getRootVehicle();
        navigation.open(claim.gate());
        pillager.getLookControl().lookAt(player, 30, 30);
        var context = new AbstractRitual.Context(player, claim);
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(DrakeSoulbinding.restrained(player)
                ? run.displayedItem(context, index) : run.holds(index) ? new ItemStack(Items.LEAD) : ItemStack.EMPTY);
        if (DrakeSoulbinding.restrained(player)) {
            DrakeBattleGoal.of(pillager).stopPursuit();
            DrakeSoulbinding.role(pillager, pillager.squaredDistanceTo(run.position(claim, index)) <= 1.44
                    ? run.actorRole(context, index) : 0);
            if (run.paw() >= 0) DrakeSoulbinding.shoeingPaw(pillager, run.paw());
            takePosition(claim, index);
            return;
        }
        DrakeSoulbinding.role(pillager, 0);
        int guideIndex = run.composition().index("guide");
        var guide = player.getServerWorld().getEntity(claim.attendants.get(guideIndex));
        boolean leashed = DrakeLeashing.holder(player) == guide;
        if (!leashed && DrakeBattleGoal.of(pillager).pursue(player)) return;
        if (index != guideIndex) {
            if (leashed || DrakeRiding.inInteractionReach(pillager, player)) DrakeBattleGoal.of(pillager).stopPursuit();
            move(leashed ? DrakeSoulbinding.attendancePosition(claim, index) : player.getPos().add(index == 1 ? 1.5 : -1.5, 0, 0));
            return;
        }
        if (!leashed) {
            if (!DrakeRiding.inInteractionReach(pillager, player) || !pillager.getVisibilityCache().canSee(player)) { move(player.getPos()); return; }
            DrakeBattleGoal.of(pillager).movePursuit(player, null, 0, false); navigation.stop();
            if (DrakeLeashing.attachPillager(player, pillager)) {
                var home = navigation.stable();
                sourceGate = home == null ? null : home.gateAt(player.getPos());
                if (claim.gate().equals(sourceGate)) sourceGate = null;
                atGate = claim.stall().contains(player.getPos());
            }
            return;
        }
        if (sourceGate != null) {
            navigation.open(sourceGate);
            if (DrakeStableLayout.outsideGate(claim.stable, sourceGate, player.getBoundingBox(), .5)
                    && DrakeStableLayout.outsideGate(claim.stable, sourceGate, body.getBoundingBox(), .5)) {
                navigation.close(sourceGate); sourceGate = null;
            } else { move(DrakeStableLayout.gatePoint(claim.stable, sourceGate, -3.5)); return; }
        }
        if (claim.stall().contains(player.getPos()) && player.squaredDistanceTo(DrakeSoulbinding.hayPosition(claim)) <= 2.25) {
            DrakeBattleGoal.of(pillager).stopPursuit();
            takePosition(claim, guideIndex); return;
        }
        var gate = DrakeStableLayout.gatePoint(claim.stable, claim.gate(), -1.5);
        if (Math.abs(body.getX() - gate.x) < .3 && Math.abs(body.getZ() - gate.z) < .5
                && body.getY() >= gate.y - .2 && body.squaredDistanceTo(player) < 16) atGate = true;
        var target = atGate ? DrakeSoulbinding.hayPosition(claim).add(0, 0, 1.8 * DrakeStableLayout.inward(claim.stable, claim.gate())) : gate;
        if (body.squaredDistanceTo(player) > 49) {
            DrakeBattleGoal.of(pillager).movePursuit(player, null, 0, false); navigation.stop();
        }
        else if (atGate || body.squaredDistanceTo(gate) < 2.25) {
            if (DrakeBattleGoal.of(pillager).movePursuit(player, target, .8, true)) return;
            navigation.stop();
            pillager.getMoveControl().moveTo(target.x, target.y, target.z, .8);
        } else move(target);
    }

    private void takePosition(DrakeOutpostOwnership.Claim claim, int index) {
        var target = DrakeSoulbinding.attendancePosition(claim, index);
        var bed = DrakeSoulbinding.hayPosition(claim);
        if (claim.ritualRun.composition().participants().get(index).escortRequired() && Math.abs(pillager.getX() - target.x) > .6
                && (pillager.getZ() - bed.z) * DrakeStableLayout.inward(claim.stable, claim.gate()) > .6)
            target = new Vec3d(target.x, target.y, bed.z + 2.2 * DrakeStableLayout.inward(claim.stable, claim.gate()));
        move(target);
    }

    private void move(Vec3d target) {
        var navigation = pillager.getNavigation();
        double distance = pillager.getRootVehicle().squaredDistanceTo(target);
        if (DrakeBattleGoal.of(pillager).movePursuit(player, distance <= .16 ? null : target, .8, distance < 4)) return;
        if (distance <= (DrakeSoulbinding.shoeing(player) ? .01 : .16)) navigation.stop();
        else if (distance < 4) {
            navigation.stop();
            pillager.getMoveControl().moveTo(target.x, target.y, target.z, .65);
        }
        else if (pillager.age >= nextPath) {
            nextPath = pillager.age + 10;
            navigation.startMovingTo(target.x, target.y, target.z, .8);
        }
    }

    @Override public void stop() {
        DrakeBattleGoal.of(pillager).stopPursuit();
        DrakeSoulbinding.role(pillager, 0);
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(ItemStack.EMPTY);
        player = null; pillager.getNavigation().stop();
    }
}
