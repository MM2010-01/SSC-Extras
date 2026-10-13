package sscextras.drake;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

final class DrakeBoundReturn {
    static final int PULL_DELAY = 3 * 20, STUCK_TICKS = 5 * 20, BLACKOUT_TICKS = 40;
    static final double MIN_PROGRESS = 5;

    private DrakeBoundReturn() { }

    static final class Progress {
        private long since = -1;
        private double distance;

        boolean started() { return since >= 0; }
        void clear() { since = -1; distance = 0; }
        boolean sample(long time, double remaining) {
            if (since < 0 || time < since) { since = time; distance = remaining; return false; }
            if (time - since < STUCK_TICKS) return false;
            double progress = distance - remaining;
            since = time; distance = remaining;
            return progress < MIN_PROGRESS;
        }
    }

    static void clear(DrakeOutpostOwnership.Claim claim) {
        claim.returnOutsideSince = -1;
        claim.returnProgress.clear();
    }

    static boolean ready(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        long time = player.getWorld().getTime();
        if (claim.returnOutsideSince < 0) claim.returnOutsideSince = time;
        return time - claim.returnOutsideSince >= PULL_DELAY;
    }

    static boolean tick(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        long time = player.getWorld().getTime();
        var box = claim.stable;
        double dx = Math.max(Math.max(box.getMinX() - player.getX(), player.getX() - (box.getMaxX() + 1)), 0);
        double dz = Math.max(Math.max(box.getMinZ() - player.getZ(), player.getZ() - (box.getMaxZ() + 1)), 0);
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (!claim.returnProgress.sample(time, distance) || !returnToStall(player, claim)) return false;
        player.sendMessage(Text.translatable("message.ssc-extras.drake.bound_return_stall").formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC), false);
        clear(claim);
        return true;
    }

    static boolean returnToStall(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        var world = player.getServerWorld();
        if (!claim.world.equals(world.getRegistryKey())) return false;
        world.getChunk(claim.bed());
        var pos = DrakeSoulbinding.respawnPosition(player, world, claim);
        if (pos == null) return false;
        DrakeLeashing.detach(player, false);
        DrakeFeralization.stop(player, claim);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, BLACKOUT_TICKS, 0, false, false, false));
        player.teleport(world, pos.x, pos.y, pos.z, DrakeStableLayout.outwardYaw(claim.stable, claim.gate()), 0);
        player.fallDistance = 0;
        player.setVelocity(net.minecraft.util.math.Vec3d.ZERO);
        return true;
    }
}
