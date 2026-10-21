package sscextras.player;

import java.util.function.Supplier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import sscextras.drake.DrakeFeralization;
import sscextras.drake.DrakeSoulbinding;
import sscextras.guardian.RuneboundGuardian;

public final class PlayerControl {
    public interface State { Runtime sscExtras$bodyControl(); }
    public static final class Runtime { private Handle active; }
    public static final class Handle implements AutoCloseable {
        private final ServerPlayerEntity player;
        private final Identifier source;
        private final PlayerBrain brain;
        private int remaining;
        private final boolean timed;
        private boolean stopped;
        private Handle(ServerPlayerEntity player, Identifier source, PlayerBrain brain, int ticks) {
            this.player = player; this.source = source; this.brain = brain; remaining = ticks;
            timed = ticks > 0;
        }
        public Identifier source() { return source; }
        public PlayerBrain brain() { return brain; }
        public int remainingTicks() { return remaining; }
        public boolean active() { return !stopped && runtime(player).active == this; }
        @Override public void close() {
            if (!active()) return;
            stopped = true;
            runtime(player).active = null;
            try { brain.stop(); }
            finally {
                player.setJumping(false); player.setSprinting(false); player.setVelocity(Vec3d.ZERO);
                ((DrakeFeralization.State)player).sscExtras$feralControl(false);
            }
        }
    }
    private PlayerControl() { }
    private static Runtime runtime(PlayerEntity player) { return ((State)player).sscExtras$bodyControl(); }
    public static boolean active(PlayerEntity player) { return ((DrakeFeralization.State)player).sscExtras$feralControl(); }
    public static Handle current(ServerPlayerEntity player) { return runtime(player).active; }
    public static boolean owns(ServerPlayerEntity player, Identifier source) {
        var handle = current(player); return handle != null && handle.source.equals(source);
    }
    public static boolean available(ServerPlayerEntity player) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || player.hasVehicle() || player.hasPassengers()
                || DrakeSoulbinding.ritualActive(player) || DrakeSoulbinding.restrained(player)
                || sscextras.petrification.Petrification.frozen(player)) return false;
        var guardian = RuneboundGuardian.state(player);
        return !guardian.detached && !guardian.deactivated && !guardian.protecting && guardian.ritualTicks == 0;
    }
    public static Handle tryStart(ServerPlayerEntity player, Identifier source, Supplier<? extends PlayerBrain> factory, int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Negative body-control duration");
        if (current(player) != null || !available(player)) return null;
        PlayerBrain brain = java.util.Objects.requireNonNull(factory.get());
        if (brain.world() != player.getWorld()) { brain.stop(); return null; }
        var handle = new Handle(player, source, brain, ticks);
        runtime(player).active = handle;
        player.clearActiveItem(); player.closeHandledScreen(); player.setSprinting(false); player.setSneaking(false);
        ((DrakeFeralization.State)player).sscExtras$feralControl(true);
        return handle;
    }
    public static void stop(ServerPlayerEntity player, Identifier source) {
        var handle = current(player); if (handle != null && handle.source.equals(source)) handle.close();
    }
    public static void clear(ServerPlayerEntity player) {
        var handle = current(player); if (handle != null) handle.close();
        else ((DrakeFeralization.State)player).sscExtras$feralControl(false);
    }
    public static void tick(ServerPlayerEntity player) {
        var handle = current(player);
        if (handle == null) return;
        if (!available(player) || handle.brain.world() != player.getWorld() || handle.timed && handle.remaining == 0) { handle.close(); return; }
        try { handle.brain.think(); }
        catch (RuntimeException | Error error) { handle.close(); throw error; }
        if (handle.remaining > 0) handle.remaining--;
    }
    public static Vec3d movement(PlayerEntity player) {
        if (player.getWorld().isClient || !active(player)) return Vec3d.ZERO;
        var handle = runtime(player).active;
        return handle == null ? Vec3d.ZERO : handle.brain.movement();
    }
}
