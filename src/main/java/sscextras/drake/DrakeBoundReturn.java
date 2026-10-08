package sscextras.drake;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import java.util.Comparator;

public final class DrakeBoundReturn {
    static final int RETURN_DELAY = 5 * 20;

    private DrakeBoundReturn() { }

    public static void dismounted(ServerPlayerEntity player, PillagerEntity rider) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null || !DrakeRoaming.mustReturn(player, claim)) return;
        claim.returnRider = rider.getId();
        claim.nextReturnSearch = 0;
    }

    static void clear(DrakeOutpostOwnership.Claim claim) {
        claim.returnRider = claim.returnGuide = 0;
        claim.returnToStall = false;
        claim.returnAloneSince = -1;
        claim.nextReturnSearch = 0;
    }

    static PlayerEntity assigned(PillagerEntity guard) {
        for (var player : guard.getWorld().getPlayers()) {
            var claim = DrakeOutpostOwnership.claim(player);
            if (claim != null && claim.returnGuide == guard.getId() && DrakeRoaming.mustReturn(player, claim)) return player;
        }
        return null;
    }

    static Vec3d destination(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (claim.returnToStall) return Vec3d.ofBottomCenter(claim.bed());
        var nearest = new Vec3d(MathHelper.clamp(player.getX(), claim.stable.getMinX(), claim.stable.getMaxX() + 1),
                player.getY(), MathHelper.clamp(player.getZ(), claim.stable.getMinZ(), claim.stable.getMaxZ() + 1));
        var point = nearest.add(player.getPos().subtract(nearest).normalize().multiply(DrakeStableLayout.roamRange(claim.stable) - 8));
        return Vec3d.ofBottomCenter(player.getWorld().getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(point)));
    }

    private static boolean available(PillagerEntity guard, ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (!guard.isAlive() || guard.isRemoved() || guard.isAiDisabled() || guard.hasVehicle() || guard.hasPassengers()
                || guard.hasActiveRaid() || DrakeFaction.fighting(guard) || guard instanceof DrakeVisitorEntity
                || MountMerchants.trader(guard) || DrakeSoulbinding.attendee(guard) != null || !DrakeLeashing.canGuide(player, guard)) return false;
        var capture = ((DrakeCaptureGoal.Captor)guard).sscExtras$captureGoal();
        if (capture.quarry() != player && !DrakeSoulbinding.available(guard)) return false;
        var other = assigned(guard);
        if (other != null && other != player) return false;
        var navigation = (DrakeStableNavigation)guard.getNavigation();
        var home = navigation.stable();
        if (guard.getId() != claim.returnRider && (home == null || !claim.matches(player.getWorld(), home))) return false;
        return DrakeLeashing.holder(player) == guard
                    || DrakeRiding.inInteractionReach(guard, player) && guard.getVisibilityCache().canSee(player)
                    || navigation.reaches(player.getBlockPos());
    }

    static boolean tick(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (MountMarket.get(player.getServer()).busy(player)) { clear(claim); return false; }
        claim.returnToStall |= DrakeCaptureGoal.recallTime(player.getWorld());
        long time = player.getWorld().getTime();
        if (DrakeLeashing.holder(player) != null && !DrakeLeashing.holder(player).isAlive()) DrakeLeashing.detach(player, false);
        var current = player.getWorld().getEntityById(claim.returnGuide);
        if (time >= claim.nextReturnSearch || claim.returnGuide != 0 && (current == null || !current.isAlive())) {
            claim.nextReturnSearch = time + 20;
            PillagerEntity guide = current instanceof PillagerEntity guard && available(guard, player, claim) ? guard : null;
            if (guide == null && DrakeLeashing.holder(player) instanceof PillagerEntity holder && available(holder, player, claim)) guide = holder;
            if (guide == null && player.getWorld().getEntityById(claim.returnRider) instanceof PillagerEntity rider
                    && (!DrakeLeashing.attached(player) || DrakeLeashing.holder(player) == rider)
                    && available(rider, player, claim)) guide = rider;
            if (guide == null && (!DrakeLeashing.attached(player) || DrakeLeashing.holder(player) == current))
                guide = player.getWorld().getEntitiesByClass(PillagerEntity.class, player.getBoundingBox().expand(64),
                        guard -> available(guard, player, claim)).stream()
                        .min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
            claim.returnGuide = guide == null ? 0 : guide.getId();
            if (guide != null) ((DrakeCaptureGoal.Captor)guide).sscExtras$captureGoal().recallAfterDismount();
        }
        if (claim.returnGuide != 0) { claim.returnAloneSince = -1; return true; }
        if (claim.returnAloneSince < 0) claim.returnAloneSince = time;
        if (time - claim.returnAloneSince < RETURN_DELAY) return false;
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
