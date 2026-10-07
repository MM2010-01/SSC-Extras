package sscextras.drake;

import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import sscextras.events.EventAssignments;
import sscextras.events.NpcEvent;
import java.util.List;

public final class LeashEscortEvent extends NpcEvent {
    public record Template(double separation, int pathInterval, int timeout) {
        public Template {
            if (!Double.isFinite(separation) || separation <= 0 || pathInterval <= 0 || timeout <= 0)
                throw new IllegalArgumentException("Invalid escort template");
        }
        public static final Template STANDARD = new Template(7, 10, 3600);
    }
    public record Route(RegistryKey<World> dimension, Vec3d from, Vec3d to) { }
    private final PillagerEntity guide;
    private final PlayerEntity target;
    private final Template template;
    private final Route route;
    private final EventAssignments assignments;
    private final NpcEvent reservationOwner;
    private final long started;
    private long nextPath;
    private Vec3d lastDestination;
    private boolean recovering;
    private boolean keepLeash;

    private LeashEscortEvent(PillagerEntity guide, PlayerEntity target, Vec3d destination, Template template, NpcEvent parent) {
        this.guide = guide; this.target = target; this.template = template;
        route = new Route(target.getWorld().getRegistryKey(), target.getPos(), destination);
        assignments = DrakeOutpostOwnership.get(target.getServer()).assignments();
        reservationOwner = parent == null ? this : parent;
        started = guide.age;
    }
    public static LeashEscortEvent start(PillagerEntity guide, PlayerEntity target, Vec3d destination, NpcEvent parent) {
        return start(guide, target, destination, Template.STANDARD, parent);
    }
    public static LeashEscortEvent start(PillagerEntity guide, PlayerEntity target, Vec3d destination, Template template, NpcEvent parent) {
        if (target.getWorld().isClient || guide.getWorld() != target.getWorld() || DrakeLeashing.holder(target) != guide) return null;
        var event = new LeashEscortEvent(guide, target, destination, template, parent);
        if (parent == null) {
            if (!event.assignments.acquire(event, List.of(guide.getUuid(), target.getUuid()))) return null;
        } else if (parent.status() == Status.FINISHED || event.assignments.owner(guide.getUuid()) != parent
                || event.assignments.owner(target.getUuid()) != parent) return null;
        event.resume(); return event;
    }
    public Route route() { return route; }
    public void handoff() { keepLeash = true; finish(); }
    public boolean valid() {
        return status() != Status.FINISHED && target.isAlive() && guide.isAlive() && guide.getWorld() == target.getWorld()
                && route.dimension().equals(target.getWorld().getRegistryKey()) && DrakeLeashing.holder(target) == guide
                && assignments.owner(guide.getUuid()) == reservationOwner && assignments.owner(target.getUuid()) == reservationOwner
                && guide.age - started < template.timeout();
    }
    public boolean move(Vec3d waypoint, double speed, boolean direct) {
        if (!valid()) { finish(); return false; }
        var recovery = DrakeLeashing.recoveryDestination(target, guide);
        boolean nextRecovery = recovery != null;
        if (nextRecovery != recovering) { nextPath = 0; recovering = nextRecovery; }
        if (recovery != null) { waypoint = recovery; direct = false; pause(); }
        else {
            resume();
            if (guide.getRootVehicle().squaredDistanceTo(target) > template.separation() * template.separation()) waypoint = null;
        }
        var navigation = guide.getNavigation();
        if (waypoint == null) {
            DrakeBattleGoal.of(guide).movePursuit(target, null, 0, false); navigation.stop(); return true;
        }
        if (direct) {
            if (!DrakeBattleGoal.of(guide).movePursuit(target, waypoint, speed, true)) {
                navigation.stop(); guide.getMoveControl().moveTo(waypoint.x, waypoint.y, waypoint.z, speed);
            }
        } else if (guide.age >= nextPath || lastDestination == null || lastDestination.squaredDistanceTo(waypoint) > 1) {
            nextPath = guide.age + template.pathInterval(); lastDestination = waypoint;
            if (!DrakeBattleGoal.of(guide).movePursuit(target, waypoint, speed, false)) navigation.startMovingTo(waypoint.x, waypoint.y, waypoint.z, speed);
        }
        return true;
    }
    @Override protected void release() {
        if (reservationOwner == this) {
            if (!keepLeash && assignments.owner(target.getUuid()) == this && DrakeLeashing.holder(target) == guide) DrakeLeashing.detach(target, false);
            assignments.release(this);
        }
    }
}
