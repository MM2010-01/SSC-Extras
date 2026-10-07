package sscextras.drake;

import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import sscextras.progression.ProgressEvent;
import sscextras.progression.ProgressionState;
import java.util.UUID;

public final class MountRideSession {
    public static final Identifier RIDES = EarthenDrake.id("rides_started");
    public static final Identifier RIDDEN_TICKS = EarthenDrake.id("ridden_ticks");
    public static final Identifier DISTANCE = EarthenDrake.id("carried_milliblocks");
    private UUID rider;
    private RegistryKey<World> dimension;
    private Vec3d position;
    private long lastTick = Long.MIN_VALUE, pendingTicks;
    private double pendingDistance;

    public static void sample(ServerPlayerEntity mount) {
        var state = (DrakeRiding.State)mount;
        var session = state.sscExtras$rideSession();
        if (session == null) {
            if (!mount.hasPassengers()) return;
            session = new MountRideSession(); state.sscExtras$rideSession(session);
        }
        session.sample(mount, mount.getServer().getOverworld().getTime());
    }
    public static void flush(ServerPlayerEntity mount) {
        var session = ((DrakeRiding.State)mount).sscExtras$rideSession();
        if (session != null) session.flushPending(mount);
    }

    public void sample(ServerPlayerEntity mount, long tick) {
        var passenger = mount.getFirstPassenger();
        UUID next = mount.isAlive() && !mount.isSpectator() && !mount.hasVehicle()
                && passenger instanceof LivingEntity living && living.isAlive()
                && (!(passenger instanceof net.minecraft.entity.player.PlayerEntity player) || !player.isSpectator())
                && DrakeRiding.mountForm(mount) && DrakeRiding.accepts(mount, passenger) ? passenger.getUuid() : null;
        var world = mount.getWorld().getRegistryKey();
        boolean continuous = rider != null && world.equals(dimension) && tick - lastTick == 1;
        if (continuous) {
            pendingTicks++;
            double distance = mount.getPos().distanceTo(position);
            if (distance <= 8) pendingDistance += distance;
            position = mount.getPos();
        }
        boolean changed = !java.util.Objects.equals(rider, next) || !world.equals(dimension) || tick < lastTick;
        if (changed || pendingTicks >= 20) flushPending(mount);
        position = mount.getPos(); dimension = world; lastTick = tick;
        if (changed) {
            rider = next; pendingDistance = 0;
            if (rider != null) emit(mount, RIDES, 1);
        }
    }
    private void flushPending(ServerPlayerEntity mount) {
        if (rider == null) return;
        if (pendingTicks > 0) emit(mount, RIDDEN_TICKS, pendingTicks);
        long distance = (long)(pendingDistance * 1000);
        if (distance > 0) emit(mount, DISTANCE, distance);
        pendingTicks = 0; pendingDistance -= distance / 1000.0;
    }
    private void emit(ServerPlayerEntity mount, Identifier metric, long amount) {
        ProgressionState.get(mount.getServer()).record(mount.getUuid(), new ProgressEvent(metric, amount, rider, dimension, position));
    }
}
