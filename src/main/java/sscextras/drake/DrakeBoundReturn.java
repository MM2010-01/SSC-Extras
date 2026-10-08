package sscextras.drake;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

final class DrakeBoundReturn {
    static final int PULL_DELAY = 3 * 20, STUCK_TICKS = 5 * 20;
    static final double MIN_PROGRESS = 5;

    private DrakeBoundReturn() { }

    static void clear(DrakeOutpostOwnership.Claim claim) {
        claim.returnOutsideSince = claim.returnProgressSince = -1;
        claim.returnDistance = 0;
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
        if (claim.returnProgressSince < 0) {
            claim.returnProgressSince = time;
            claim.returnDistance = distance;
            return false;
        }
        if (time - claim.returnProgressSince < STUCK_TICKS) return false;
        double progress = claim.returnDistance - distance;
        claim.returnProgressSince = time;
        claim.returnDistance = distance;
        if (progress >= MIN_PROGRESS) return false;
        var world = player.getServerWorld();
        world.getChunk(claim.bed());
        var pos = DrakeSoulbinding.respawnPosition(player, world, claim);
        if (pos == null) return false;
        DrakeLeashing.detach(player, false);
        DrakeFeralization.stop(player, claim);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false, false));
        player.teleport(world, pos.x, pos.y, pos.z, DrakeStableLayout.outwardYaw(claim.stable, claim.gate()), 0);
        player.fallDistance = 0;
        player.sendMessage(Text.translatable("message.ssc-extras.drake.bound_return_stall").formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC), false);
        clear(claim);
        return true;
    }
}
