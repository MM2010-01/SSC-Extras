package sscextras.drake;

import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import java.util.Comparator;

public final class DrakeRoaming {
    public static final int RANGE = 64;
    private DrakeRoaming() { }

    public static boolean canSee(PillagerEntity pillager, PlayerEntity player) {
        double range = pillager.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE);
        return pillager.isAlive() && !pillager.isAiDisabled() && !pillager.hasVehicle()
                && pillager.squaredDistanceTo(player) <= range * range && pillager.getVisibilityCache().canSee(player);
    }

    static void tick(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || EarthenDrake.stage(player) < 0
                || !claim.world.equals(player.getWorld().getRegistryKey()) || DrakeBattleGoal.riding(player)) {
            claim.seenSince = -1; claim.witness = claim.escort = 0;
            return;
        }
        escort(player, claim);
        boolean inside = DrakeCaptureGoal.near(claim.stable, player.getPos(), DrakeStableLayout.roamRange(claim.stable));
        boolean edge = inside && !DrakeCaptureGoal.near(claim.stable, player.getPos(), DrakeStableLayout.roamRange(claim.stable) - 8);
        if (edge && !claim.warnedEdge && !claim.outside && player.getWorld().isDay()) hint(player, "roam_edge");
        claim.warnedEdge = edge;
        long time = player.getWorld().getTime();
        if (inside && !edge) {
            claim.outside = claim.spotted = false;
            claim.seenSince = -1; claim.witness = 0;
            return;
        }
        if (!inside && !claim.outside && time >= claim.nextOutsideHint) {
            hint(player, "roam_outside"); claim.nextOutsideHint = time + 600;
        }
        claim.outside = !inside;
        if (inside) { claim.spotted = false; claim.seenSince = -1; }
        var previous = player.getWorld().getEntityById(claim.witness);
        PillagerEntity witness = previous instanceof PillagerEntity pillager && tracking(pillager, player, claim)
                && time < claim.recallUntil ? pillager : null;
        if (witness == null) {
            claim.seenSince = -1; claim.witness = 0;
            var escort = player.getWorld().getEntityById(claim.escort);
            if (escort instanceof PillagerEntity pillager && canSee(pillager, player)) witness = pillager;
            else {
                if (time % 20 != 0) return;
                witness = player.getWorld().getEntitiesByClass(PillagerEntity.class, player.getBoundingBox().expand(64),
                        pillager -> canSee(pillager, player) && belongs(pillager, claim)).stream()
                        .min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
            }
        }
        if (witness == null) { claim.seenSince = -1; claim.witness = 0; return; }
        claim.witness = witness.getId();
        if (!witness.getVisibilityCache().canSee(player)) { claim.seenSince = -1; return; }
        claim.recallUntil = time + 1200;
        if (inside) return;
        if (!claim.spotted) { hint(player, "roam_spotted"); claim.spotted = true; }
        if (claim.seenSince < 0) claim.seenSince = time;
        if (!claim.tryingToEscape && time - claim.seenSince >= 100) {
            claim.tryingToEscape = true;
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
            hint(player, "escape_marked");
            DrakeDialogue.say(player, "escape_seen");
        }
    }

    private static boolean tracking(PillagerEntity pillager, PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        int range = DrakeStableLayout.captureRange(claim.stable);
        return pillager.isAlive() && !pillager.isAiDisabled() && !pillager.hasVehicle()
                && pillager.squaredDistanceTo(player) <= range * range
                && belongs(pillager, claim) && DrakeCaptureGoal.near(claim.stable, player.getPos());
    }

    private static boolean roaming(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator() && !player.isSleeping()
                && EarthenDrake.stage(player) >= 0 && claim.world.equals(player.getWorld().getRegistryKey())
                && player.getWorld().isDay() && !player.hasVehicle() && !player.hasPassengers()
                && !DrakeBattleGoal.assigned(player) && !DrakeSoulbinding.ritualActive(player)
                && !BondOfTheBeastCompat.hasOwner(player)
                && (!DrakeLeashing.attached(player) || !claim.tryingToEscape
                    && DrakeLeashing.holder(player) instanceof PillagerEntity holder && belongs(holder, claim));
    }

    private static void escort(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (!roaming(player, claim)) { claim.escort = 0; return; }
        if (DrakeLeashing.holder(player) instanceof PillagerEntity holder) {
            claim.escort = escortAvailable(holder, player, claim, true) ? holder.getId() : 0;
            return;
        }
        var previous = player.getWorld().getEntityById(claim.escort);
        if (previous instanceof PillagerEntity guard && escortAvailable(guard, player, claim, true)) return;
        boolean replaced = claim.escort != 0;
        claim.escort = 0;
        long time = player.getWorld().getTime();
        if (!replaced && time < claim.nextEscortSearch) return;
        claim.nextEscortSearch = time + 20;
        var guard = player.getWorld().getEntitiesByClass(PillagerEntity.class,
                Box.from(claim.stable).expand(DrakeStableLayout.captureRange(claim.stable)), candidate ->
                        escortAvailable(candidate, player, claim, false) && following(candidate) == null).stream()
                .min(Comparator.comparingDouble(player::squaredDistanceTo)).orElse(null);
        if (guard != null) claim.escort = guard.getId();
    }

    private static boolean escortAvailable(PillagerEntity guard, PlayerEntity player, DrakeOutpostOwnership.Claim claim, boolean defending) {
        return DrakeSoulbinding.available(guard, defending && DrakeRiding.canCarryPillager(player))
                && DrakeSoulbinding.attendee(guard) == null && belongs(guard, claim)
                && DrakeCaptureGoal.near(claim.stable, player.getPos());
    }

    public static PlayerEntity following(PillagerEntity pillager) {
        for (var player : pillager.getWorld().getPlayers()) {
            var claim = DrakeOutpostOwnership.claim(player);
            if (claim == null) continue;
            if (claim.escort == pillager.getId() && roaming(player, claim) && escortAvailable(pillager, player, claim, true)) return player;
            if ((claim.outside || claim.warnedEdge) && !DrakeLeashing.attached(player) && claim.witness == pillager.getId()
                    && player.getWorld().getTime() < claim.recallUntil && tracking(pillager, player, claim)) return player;
        }
        return null;
    }

    private static boolean belongs(PillagerEntity pillager, DrakeOutpostOwnership.Claim claim) {
        if (pillager instanceof DrakeVisitorEntity) return false;
        var stable = ((DrakeStableNavigation)pillager.getNavigation()).stable();
        return stable != null && claim.matches(pillager.getWorld(), stable);
    }

    private static void hint(PlayerEntity player, String key) {
        player.sendMessage(Text.translatable("message.ssc-extras.drake." + key).formatted(Formatting.YELLOW), false);
    }
}
