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
        if (claim == null) return;
        int index = claim.attendants.indexOf(pillager.getUuid());
        if (index < 0) return;
        var navigation = (DrakeStableNavigation)pillager.getNavigation();
        navigation.open(claim.gate());
        pillager.getLookControl().lookAt(player, 30, 30);
        boolean shoeing = claim.shoeingRitual;
        boolean punishment = !shoeing && DrakeSoulbinding.punishment(claim);
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(shoeing && index == 2 && DrakeSoulbinding.restrained(player)
                ? DrakeEquipment.SHOES.getDefaultStack()
                : shoeing && DrakeSoulbinding.restrained(player) ? ItemStack.EMPTY
                : DrakeSoulbinding.role(pillager) == DrakeSoulbinding.FEEDING
                ? new ItemStack(BeastizationCatalyst.ITEM)
                : (punishment ? index != 0 : index < 2) ? new ItemStack(Items.LEAD) : ItemStack.EMPTY);
        if (DrakeSoulbinding.restrained(player)) {
            if (shoeing && index == 2) {
                DrakeSoulbinding.role(pillager, pillager.squaredDistanceTo(DrakeSoulbinding.attendancePosition(claim, index)) <= 1.44
                        ? DrakeSoulbinding.SHOEING : 0);
                if (DrakeSoulbinding.role(pillager) == DrakeSoulbinding.SHOEING)
                    DrakeSoulbinding.shoeingPaw(pillager, Math.min(3, claim.shoeingTicks / DrakeShoes.PAW_TICKS));
            }
            else if (punishment ? index != 0 : index < 2) DrakeSoulbinding.role(pillager, DrakeSoulbinding.HOLDING);
            takePosition(claim, index);
            return;
        }
        DrakeSoulbinding.role(pillager, 0);
        var guide = player.getServerWorld().getEntity(claim.attendants.get(0));
        boolean leashed = DrakeLeashing.holder(player) == guide;
        if (index != 0) {
            move(leashed ? DrakeSoulbinding.attendancePosition(claim, index) : player.getPos().add(index == 1 ? 1.5 : -1.5, 0, 0));
            return;
        }
        if (!leashed) {
            move(player.getPos());
            if (pillager.squaredDistanceTo(player) > 4 || !pillager.getVisibilityCache().canSee(player)) return;
            for (var id : claim.attendants) {
                var other = player.getServerWorld().getEntity(id);
                if (other == null || other.squaredDistanceTo(player) > 36) return;
            }
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
                    && DrakeStableLayout.outsideGate(claim.stable, sourceGate, pillager.getBoundingBox(), .5)) {
                navigation.close(sourceGate); sourceGate = null;
            } else { move(DrakeStableLayout.gatePoint(claim.stable, sourceGate, -3.5)); return; }
        }
        if (claim.stall().contains(player.getPos()) && player.squaredDistanceTo(DrakeSoulbinding.hayPosition(claim)) <= 2.25) {
            takePosition(claim, 0); return;
        }
        var gate = DrakeStableLayout.gatePoint(claim.stable, claim.gate(), -1.5);
        if (Math.abs(pillager.getX() - gate.x) < .3 && Math.abs(pillager.getZ() - gate.z) < .5
                && pillager.getY() >= gate.y - .2 && pillager.squaredDistanceTo(player) < 16) atGate = true;
        var target = atGate ? DrakeSoulbinding.hayPosition(claim).add(0, 0, 1.8 * DrakeStableLayout.inward(claim.stable, claim.gate())) : gate;
        if (pillager.squaredDistanceTo(player) > 49) navigation.stop();
        else if (atGate || pillager.squaredDistanceTo(gate) < 2.25) {
            navigation.stop();
            pillager.getMoveControl().moveTo(target.x, target.y, target.z, .8);
        } else move(target);
    }

    private void takePosition(DrakeOutpostOwnership.Claim claim, int index) {
        var target = DrakeSoulbinding.attendancePosition(claim, index);
        var bed = DrakeSoulbinding.hayPosition(claim);
        if (index < 2 && Math.abs(pillager.getX() - target.x) > .6
                && (pillager.getZ() - bed.z) * DrakeStableLayout.inward(claim.stable, claim.gate()) > .6)
            target = new Vec3d(target.x, target.y, bed.z + 2.2 * DrakeStableLayout.inward(claim.stable, claim.gate()));
        move(target);
    }

    private void move(Vec3d target) {
        var navigation = pillager.getNavigation();
        double distance = pillager.squaredDistanceTo(target);
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
        DrakeSoulbinding.role(pillager, 0);
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(ItemStack.EMPTY);
        player = null; pillager.getNavigation().stop();
    }
}
