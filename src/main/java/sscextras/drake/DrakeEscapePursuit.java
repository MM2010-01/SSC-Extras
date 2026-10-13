package sscextras.drake;

import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import sscextras.collar.CollarSlots;
import sscextras.collar.Collars;
import java.util.LinkedHashMap;
import java.util.Map;

final class DrakeEscapePursuit {
    static final int LOCATION_MEMORY = 30 * 20;
    static final int SEARCH_TIME = 60 * 20;
    static final int COLLAR_RANGE = 64;
    static final int SEARCH_RADIUS = 32;
    final PlayerEntity player;
    final DrakeStablePiece stable;
    private final DrakeOutpostOwnership.Claim claim;
    private final Map<PillagerEntity, Search> hunters = new LinkedHashMap<>();
    private Vec3d lastKnown;
    private long lastDetected, lastUpdate = Long.MIN_VALUE;
    private boolean unknown, finished;

    private static final class Search {
        Vec3d destination;
        long nextChoice, expires;
        int sweep;
        void clear() { destination = null; nextChoice = expires = 0; sweep = 0; }
    }

    private DrakeEscapePursuit(PlayerEntity player, DrakeStablePiece stable, DrakeOutpostOwnership.Claim claim) {
        this.player = player; this.stable = stable; this.claim = claim;
        lastKnown = player.getRootVehicle().getPos();
        lastDetected = player.getWorld().getTime();
    }

    static boolean collared(PlayerEntity player) {
        return CollarSlots.get(player).stream().anyMatch(slot -> slot.stack().isOf(Collars.CURSED)
                || slot.stack().isOf(Collars.TAMING));
    }

    static boolean detects(PillagerEntity guard, PlayerEntity player) {
        return detects(guard, player, collared(player));
    }

    private static boolean detects(PillagerEntity guard, PlayerEntity player, boolean collared) {
        return guard.isAlive() && !guard.isRemoved() && !guard.isAiDisabled() && guard.getWorld() == player.getWorld()
                && (DrakeRoaming.canSee(guard, player) || collared && guard.squaredDistanceTo(player) <= COLLAR_RANGE * COLLAR_RANGE);
    }

    static void alert(PillagerEntity witness, PlayerEntity player, DrakeStablePiece stable) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null || !claim.tryingToEscape || claim.soulbound || !claim.matches(player.getWorld(), stable)
                || DrakeLeashing.attached(player) || !detects(witness, player)) return;
        var pursuit = claim.pursuit;
        if (pursuit == null || !pursuit.active()) {
            pursuit = new DrakeEscapePursuit(player, stable, claim);
            claim.pursuit = pursuit;
        }
        pursuit.add(witness);
        for (var guard : player.getWorld().getEntitiesByClass(PillagerEntity.class,
                Box.from(stable.getBoundingBox()).expand(DrakeStableLayout.captureRange(claim.stable)), guard ->
                        DrakeCaptureGoal.near(stable, guard.getPos()))) pursuit.add(guard);
        for (var guard : player.getWorld().getEntitiesByClass(PillagerEntity.class,
                witness.getBoundingBox().expand(COLLAR_RANGE), guard -> guard.squaredDistanceTo(witness) <= COLLAR_RANGE * COLLAR_RANGE))
            pursuit.add(guard);
        if (pursuit.hunters.isEmpty()) pursuit.finish();
    }

    private void add(PillagerEntity guard) {
        if (hunters.containsKey(guard) || !available(guard)) return;
        var goal = ((DrakeCaptureGoal.Captor)guard).sscExtras$captureGoal();
        if (goal != null && goal.alert(this)) hunters.put(guard, new Search());
    }

    private boolean available(PillagerEntity guard) {
        var ride = DrakeBattleGoal.of(guard);
        var capture = ((DrakeCaptureGoal.Captor)guard).sscExtras$captureGoal();
        return guard.isAlive() && !guard.isRemoved() && !guard.isAiDisabled() && guard.getWorld() == player.getWorld()
                && !(guard instanceof DrakeVisitorEntity) && !MountMerchants.trader(guard) && !guard.hasActiveRaid()
                && !DrakeFaction.fighting(guard) && (!guard.hasVehicle() || ride != null && ride.pursuing(player))
                && (ride == null || ride.mount() == null || ride.pursuing(player))
                && (capture == null || capture.quarry() == null || capture.quarry() == player)
                && ((DrakeFaction.EquipmentDisplay)guard).sscExtras$recruiting() == null
                && DrakeOutpostOwnership.get(player.getServer()).assignments().owner(guard.getUuid()) == null;
    }

    boolean active() {
        if (finished) return false;
        if (DrakeOutpostOwnership.claim(player) != claim || !claim.tryingToEscape || claim.soulbound
                || !claim.world.equals(player.getWorld().getRegistryKey()) || player.isRemoved()
                || !DrakeCaptureGoal.eligible(player) || DrakeLeashing.attached(player)
                || DrakeBattleGoal.assigned(player) || DrakeSoulbinding.ritualActive(player)) {
            finish(); return false;
        }
        long time = player.getWorld().getTime();
        if (lastUpdate == time) return true;
        lastUpdate = time;
        hunters.keySet().removeIf(guard -> !available(guard));
        if (hunters.isEmpty()) { finish(); return false; }
        boolean collar = collared(player);
        if (hunters.keySet().stream().anyMatch(guard -> detects(guard, player, collar))) {
            lastKnown = player.getRootVehicle().getPos(); lastDetected = time; unknown = false;
            hunters.values().forEach(Search::clear);
        }
        if (time - lastDetected >= LOCATION_MEMORY + SEARCH_TIME) { finish(); return false; }
        if (!unknown && time - lastDetected >= LOCATION_MEMORY) {
            unknown = true;
            player.sendMessage(Text.translatable("message.ssc-extras.drake.pursuit_lost").formatted(Formatting.YELLOW), false);
        }
        return true;
    }

    boolean includes(PillagerEntity guard) { return active() && hunters.containsKey(guard); }
    Vec3d destination(PillagerEntity guard) {
        if (!includes(guard) || !unknown) return lastKnown;
        var search = hunters.get(guard);
        long time = player.getWorld().getTime();
        var body = guard.getRootVehicle();
        if (time < search.nextChoice || search.destination != null && time < search.expires
                && body.squaredDistanceTo(search.destination) > 4) return search.destination == null ? lastKnown : search.destination;
        var previous = search.destination;
        search.destination = null;
        search.nextChoice = time + 20;
        int sector = 0;
        for (var other : hunters.keySet()) { if (other == guard) break; sector++; }
        double angle = Math.PI * 2 * sector / hunters.size() + search.sweep * Math.PI / 3;
        int radius = 8 + search.sweep++ % 4 * 8;
        var navigation = (DrakeStableNavigation)guard.getNavigation();
        for (int attempt = 0; attempt < 8; attempt++) {
            double direction = angle + attempt * Math.PI / 4;
            var candidate = BlockPos.ofFloored(lastKnown.add(Math.cos(direction) * radius, 0, Math.sin(direction) * radius));
            if (!guard.getWorld().isChunkLoaded(candidate)) continue;
            var path = navigation.findPathTo(candidate, 0);
            if (path == null || !path.reachesTarget() || path.getLength() == 0) continue;
            var point = Vec3d.ofBottomCenter(path.getNodePos(path.getLength() - 1));
            double dx = point.x - lastKnown.x, dz = point.z - lastKnown.z;
            if (dx * dx + dz * dz > (SEARCH_RADIUS + 1) * (SEARCH_RADIUS + 1)
                    || Math.abs(point.y - lastKnown.y) > 8 || body.squaredDistanceTo(point) < 9
                    || previous != null && previous.squaredDistanceTo(point) < 16
                    || hunters.values().stream().anyMatch(other -> other.destination != null && other.destination.squaredDistanceTo(point) < 36)) continue;
            search.destination = point; search.expires = time + 160;
            return point;
        }
        return lastKnown;
    }
    Vec3d lastKnown() { return lastKnown; }
    boolean unknown() { return unknown; }
    boolean finished() { return finished; }

    void remove(PillagerEntity guard) {
        hunters.remove(guard);
        if (hunters.isEmpty()) finish();
    }

    void finish() {
        if (finished) return;
        finished = true;
        if (claim.pursuit == this) claim.pursuit = null;
        for (var guard : hunters.keySet()) ((DrakeCaptureGoal.Captor)guard).sscExtras$captureGoal().clearAlert(this);
        hunters.clear();
    }
}
