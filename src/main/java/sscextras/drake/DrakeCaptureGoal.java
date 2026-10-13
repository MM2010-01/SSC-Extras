package sscextras.drake;

import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;

public final class DrakeCaptureGoal extends Goal {
    public static final int RANGE = 128;
    private static final Identifier OUTPOST = new Identifier("minecraft", "pillager_outpost");
    private static final Identifier MANSION = new Identifier("minecraft", "mansion");
    public interface Captor { DrakeCaptureGoal sscExtras$captureGoal(); }
    private final PillagerEntity pillager;
    private DrakeStablePiece stable;
    private PlayerEntity player;
    private BlockPos sourceGate;
    private int nextSearch, started, nextDisplay;
    private int targetStall = -1;
    private boolean atGate, leaving, complete;
    private LeashEscortEvent escort;
    private DrakeEscapePursuit pursuit;
    private int nextPursuitPath;
    private final DrakeBoundReturn.Progress returnProgress = new DrakeBoundReturn.Progress();
    private long recoveryHintAt = -1;

    public DrakeCaptureGoal(PillagerEntity pillager) {
        this.pillager = pillager;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    public static boolean eligible(PlayerEntity player) {
        return DrakeLeashing.eligible(player) && !player.isCreative() && !BondOfTheBeastCompat.hasOwner(player) && !MountMarket.playerOwned(player)
                && !MountMarket.get(player.getServer()).busy(player)
                && (DrakeOutpostOwnership.claim(player) != null || cursedHarness(player));
    }

    private static boolean cursedHarness(PlayerEntity player) {
        return !DrakeEquipment.equipped(player, DrakeEquipment.REINS).isEmpty()
                && !DrakeEquipment.equipped(player, DrakeEquipment.SADDLE).isEmpty();
    }

    public static boolean recallTime(net.minecraft.world.World world) {
        return Math.floorMod(world.getTimeOfDay(), 24000) >= 12000;
    }

    private boolean wants(PlayerEntity player) {
        if (!eligible(player) || DrakeBattleGoal.assigned(player) || DrakeSoulbinding.ritualActive(player)) return false;
        if (!DrakeOutpostOwnership.owns(player, stable)) return cursedHarness(player);
        var claim = DrakeOutpostOwnership.claim(player);
        if (DrakeRoaming.mustReturn(player, claim)) return false;
        if (claim.tryingToEscape) return DrakeLeashing.holder(player) == pillager
                || hunting(player) || DrakeEscapePursuit.detects(pillager, player);
        return recallTime(pillager.getWorld()) || EarthenDrake.stage(player) < 2;
    }

    public static boolean near(DrakeStablePiece piece, Vec3d position) {
        return near(piece.getBoundingBox(), position);
    }

    public static boolean near(net.minecraft.util.math.BlockBox box, Vec3d position) {
        return near(box, position, DrakeStableLayout.captureRange(box));
    }

    public static boolean near(net.minecraft.util.math.BlockBox box, Vec3d position, int range) {
        double dx = Math.max(Math.max(box.getMinX() - position.x, position.x - (box.getMaxX() + 1)), 0);
        double dz = Math.max(Math.max(box.getMinZ() - position.z, position.z - (box.getMaxZ() + 1)), 0);
        return dx * dx + dz * dz < range * range;
    }

    public static Box stall(DrakeStablePiece piece) {
        return piece.stall(piece.stallCount() - 1);
    }

    public static DrakeStablePiece findStable(ServerWorld world, BlockPos pos) {
        return findStable(world, pos, RANGE, true);
    }

    public static DrakeStablePiece findStable(ServerWorld world, BlockPos pos, int range) {
        return findStable(world, pos, range, false);
    }

    private static DrakeStablePiece findStable(ServerWorld world, BlockPos pos, int range, boolean useStableRange) {
        return findStable(world, pos, range, useStableRange, stable -> true);
    }

    public static DrakeStablePiece findStable(ServerWorld world, BlockPos pos, int range, java.util.function.Predicate<DrakeStablePiece> available) {
        return findStable(world, pos, range, false, available);
    }

    private static DrakeStablePiece findStable(ServerWorld world, BlockPos pos, int range, boolean useStableRange,
                                               java.util.function.Predicate<DrakeStablePiece> available) {
        var structures = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
        var outpost = structures.get(OUTPOST);
        var mansion = structures.get(MANSION);
        var checkedOutposts = new LongOpenHashSet();
        var checkedMansions = new LongOpenHashSet();
        var pending = new LongLinkedOpenHashSet();
        var starts = new ArrayList<net.minecraft.structure.StructureStart>();
        var checked = new HashSet<net.minecraft.structure.StructureStart>();
        var chunk = new ChunkPos(pos);
        var center = Vec3d.ofCenter(pos);
        var accessor = world.getStructureAccessor();
        DrakeStablePiece closest = null;
        double distance = Double.MAX_VALUE;
        int chunks = range / 16 + 1;
        for (int x = chunk.x - chunks; x <= chunk.x + chunks; x++) for (int z = chunk.z - chunks; z <= chunk.z + chunks; z++) {
            if (!world.isChunkLoaded(x, z)) continue;
            starts.clear();
            var references = world.getChunk(x, z, ChunkStatus.STRUCTURE_REFERENCES).getStructureReferences();
            // Deduplicate before resolving starts, retaining native reference order for equal-distance ties.
            for (var entry : references.entrySet()) {
                var structure = entry.getKey();
                if (structure != outpost && structure != mansion) continue;
                var seen = structure == outpost ? checkedOutposts : checkedMansions;
                pending.clear();
                var iterator = entry.getValue().iterator();
                while (iterator.hasNext()) {
                    long reference = iterator.nextLong();
                    if (seen.add(reference)) pending.add(reference);
                }
                if (!pending.isEmpty()) accessor.acceptStructureStarts(structure, pending, starts::add);
            }
            for (var start : starts) {
                if (!checked.add(start)) continue;
                for (var piece : start.getChildren()) if (piece instanceof DrakeStablePiece candidate
                        && (useStableRange ? near(candidate, center) : near(candidate.getBoundingBox(), center, range))) {
                    double next = candidate.getBoundingBox().getCenter().getSquaredDistance(pos);
                    if (next < distance && available.test(candidate)) { closest = candidate; distance = next; }
                }
            }
        }
        return closest;
    }

    private boolean occupied() {
        if (!DrakeOutpostOwnership.available((ServerWorld)pillager.getWorld(), stable, targetStall, player)) return true;
        if (!pillager.getWorld().getEntitiesByClass(LivingEntity.class, stable.stall(targetStall),
                entity -> entity.isAlive() && !(entity instanceof PillagerEntity) && entity != player
                        && entity != pillager.getVehicle() && !entity.isSpectator()).isEmpty()) return true;
        return pillager.getWorld().getEntitiesByClass(PillagerEntity.class, Box.from(stable.getBoundingBox()).expand(RANGE), entity -> {
            var other = ((Captor)entity).sscExtras$captureGoal();
            return entity != pillager && other != null && other.player != null && other.player != player && other.stable != null
                    && other.stable.getBoundingBox().equals(stable.getBoundingBox()) && other.targetStall == targetStall;
        }).size() > 0;
    }

    @Override public boolean canStart() {
        if (pillager.hasVehicle() && DrakeBattleGoal.of(pillager).pursuitTarget() == null
                || pillager.age < nextSearch || DrakeFaction.fighting(pillager) || DrakeSoulbinding.attendee(pillager) != null) return false;
        nextSearch = pillager.age + 40;
        if (pursuit != null) {
            if (pursuit.includes(pillager)) {
                stable = pursuit.stable;
                player = pursuit.player;
                targetStall = DrakeOutpostOwnership.availableStall((ServerWorld)pillager.getWorld(), stable, player);
                if (targetStall >= 0 && !occupied() && wants(player)
                        && pillager.getWorld().getBlockState(stable.tie(targetStall)).isIn(BlockTags.FENCES)) return true;
                pursuit.remove(pillager);
            }
            pursuit = null; player = null; targetStall = -1;
        }
        if (pillager.getWorld().getPlayers().stream().noneMatch(candidate -> eligible(candidate)
                && pillager.squaredDistanceTo(candidate) <= (RANGE * 2 + 24) * (RANGE * 2 + 24))) {
            DrakeBattleGoal.of(pillager).stopPursuit(); return false;
        }
        stable = navigation().stable();
        if (stable == null) { DrakeBattleGoal.of(pillager).stopPursuit(); return false; }
        var candidates = pillager.getWorld().getEntitiesByClass(PlayerEntity.class, Box.from(stable.getBoundingBox()).expand(RANGE), candidate ->
                wants(candidate) && near(stable, candidate.getPos())
                && (!DrakeLeashing.attached(candidate) || DrakeLeashing.holder(candidate) == pillager));
        candidates.sort(Comparator.comparingDouble(pillager::squaredDistanceTo));
        for (var candidate : candidates) {
            if (pillager.hasVehicle() && !DrakeBattleGoal.of(pillager).pursuing(candidate)) continue;
            player = candidate;
            targetStall = DrakeOutpostOwnership.availableStall((ServerWorld)pillager.getWorld(), stable, player);
            if (targetStall < 0 || !pillager.getWorld().getBlockState(stable.tie(targetStall)).isIn(BlockTags.FENCES)) continue;
            if (DrakeLeashing.holder(player) != pillager && DrakeOutpostOwnership.owns(player, stable)
                    && stable.stall(targetStall).contains(player.getPos())) continue;
            if (!occupied() && (DrakeBattleGoal.of(pillager).pursuing(player)
                    || DrakeRiding.inInteractionReach(pillager, player) && pillager.getVisibilityCache().canSee(player)
                    || navigation().reaches(player.getRootVehicle().getBlockPos()))) return true;
        }
        DrakeBattleGoal.of(pillager).stopPursuit();
        player = null; targetStall = -1;
        return false;
    }

    @Override public boolean shouldContinue() {
        if (complete || player == null || pillager.hasVehicle() && !DrakeBattleGoal.of(pillager).pursuing(player)) return false;
        if (leaving) return player.isAlive() && !player.isRemoved() && player.getWorld() == pillager.getWorld();
        noticeEscape();
        return (!DrakeFaction.fighting(pillager) || DrakeLeashing.holder(player) == pillager) && wants(player)
                && (DrakeLeashing.holder(player) == pillager || hunting(player) || near(stable, player.getPos())) && !occupied()
                && (!DrakeLeashing.attached(player) || DrakeLeashing.holder(player) == pillager)
                && pillager.getWorld().getBlockState(stable.tie(targetStall)).isIn(BlockTags.FENCES);
    }

    @Override public void start() {
        if (escort != null) { escort.handoff(); escort = null; }
        atGate = false; leaving = false; complete = false; sourceGate = null; started = pillager.age;
        nextPursuitPath = 0;
        returnProgress.clear(); recoveryHintAt = -1;
        noticeEscape();
        gates(true);
        pillager.clearActiveItem(); pillager.setCharging(false); pillager.setTarget(null);
        nextDisplay = 0;
    }

    private void noticeEscape() {
        if (hunting(player) || DrakeLeashing.attached(player) || !eligible(player)) return;
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim != null && claim.matches(player.getWorld(), stable) && !claim.soulbound
                && !claim.stall().contains(player.getPos()) && DrakeEscapePursuit.detects(pillager, player)
                && (claim.tryingToEscape || EarthenDrake.stage(player) < 2)) {
            if (!claim.tryingToEscape) {
                claim.tryingToEscape = true;
                DrakeOutpostOwnership.get(player.getServer()).markDirty();
            }
            DrakeEscapePursuit.alert(pillager, player, stable);
        }
    }

    private void gates(boolean open) {
        if (open) navigation().open(stable.gate(targetStall));
        else navigation().close(stable.gate(targetStall));
    }

    private DrakeStableNavigation navigation() { return (DrakeStableNavigation)pillager.getNavigation(); }

    public PlayerEntity quarry() { return player; }

    boolean alert(DrakeEscapePursuit alarm) {
        if (pursuit != null && pursuit != alarm && !pursuit.finished()) return false;
        pursuit = alarm; nextSearch = pillager.age;
        return true;
    }

    void clearAlert(DrakeEscapePursuit alarm) { if (pursuit == alarm) pursuit = null; }

    private boolean hunting(PlayerEntity candidate) {
        return pursuit != null && pursuit.player == candidate && pursuit.includes(pillager);
    }

    public void recallAfterDismount() { nextSearch = pillager.age; }

    public boolean reserves(DrakeStablePiece home, int stall, PlayerEntity candidate) {
        return player != null && player != candidate && stable != null && targetStall == stall
                && stable.getBoundingBox().equals(home.getBoundingBox());
    }

    private boolean nearestCaptor() {
        if (DrakeLeashing.holder(player) == pillager) return true;
        Vec3d position = hunting(player) ? pursuit.lastKnown() : player.getPos();
        double distance = pillager.squaredDistanceTo(position);
        return pillager.getWorld().getEntitiesByClass(PillagerEntity.class, new Box(BlockPos.ofFloored(position)).expand(RANGE), candidate -> {
            var goal = ((Captor)candidate).sscExtras$captureGoal();
            if (!candidate.isAlive() || candidate == pillager || goal == null || goal.player != player) return false;
            double other = candidate.squaredDistanceTo(position);
            return other < distance || other == distance && candidate.getId() < pillager.getId();
        }).isEmpty();
    }

    public boolean holdsOpen(BlockPos gate) {
        return player != null && stable != null && !complete
                && (gate.equals(stable.gate(targetStall)) || gate.equals(sourceGate));
    }

    @Override public void tick() {
        if (escort != null && !escort.valid()) {
            escort.finish(); escort = null; complete = true; move(null, false); return;
        }
        if (!leaving && DrakeLeashing.attached(player) && DrakeLeashing.holder(player) != pillager) return;
        if (recoveryHintAt >= 0) {
            move(null, false);
            if (player.getWorld().getTime() < recoveryHintAt) return;
            player.sendMessage(net.minecraft.text.Text.translatable("message.ssc-extras.drake.leashed_return_stall")
                    .formatted(net.minecraft.util.Formatting.LIGHT_PURPLE, net.minecraft.util.Formatting.ITALIC), false);
            recoveryHintAt = -1;
        }
        boolean hunting = hunting(player);
        Vec3d quarryPosition = hunting ? pursuit.destination(pillager) : player.getRootVehicle().getPos();
        pillager.getLookControl().lookAt(quarryPosition.x, quarryPosition.y + 1, quarryPosition.z, 30, 30);
        var body = pillager.getRootVehicle();
        Vec3d gate = DrakeStableLayout.gatePoint(stable.getBoundingBox(), stable.gate(targetStall), -1.5);
        pillager.setSprinting(!leaving && !atGate && sourceGate == null && pillager.squaredDistanceTo(gate) > 100
                && (DrakeLeashing.holder(player) != pillager || pillager.squaredDistanceTo(player) < 36));
        if (pillager.age >= nextDisplay) {
            nextDisplay = pillager.age + 10;
            ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(!leaving && nearestCaptor()
                    ? new ItemStack(Items.LEAD) : ItemStack.EMPTY);
        }
        if (leaving) {
            if (DrakeStableLayout.outsideGate(stable.getBoundingBox(), stable.gate(targetStall), body.getBoundingBox(), .25)) {
                gates(false);
                complete = true; move(null, false);
            } else move(gate.add(0, 0, -DrakeStableLayout.inward(stable.getBoundingBox(), stable.gate(targetStall))), true);
            return;
        }
        if (DrakeLeashing.holder(player) != pillager) {
            returnProgress.clear();
            if (DrakeBattleGoal.of(pillager).pursue(player, quarryPosition)) return;
            if (!pillager.hasVehicle() && pillager.age >= nextPursuitPath) {
                nextPursuitPath = pillager.age + 10;
                navigation().startMovingTo(quarryPosition.x, quarryPosition.y, quarryPosition.z, 1);
            }
            if (DrakeRiding.inInteractionReach(pillager, player) && pillager.getVisibilityCache().canSee(player)) {
                boolean dismounted = player.hasVehicle();
                if (dismounted) player.stopRiding();
                if (player.hasVehicle()) return;
                if (!hunting && !dismounted && !pillager.hasVehicle() && EarthenDrake.stage(player) >= 2
                        && DrakeRiding.canCarryPillager(player) && pillager.getRandom().nextBoolean() && DrakeBattleGoal.of(pillager).recall(player, stable)) {
                    complete = true; return;
                }
                if (!DrakeLeashing.attachPillager(player, pillager)) return;
                if (pursuit != null) pursuit.finish();
                var claim = DrakeOutpostOwnership.claim(player);
                if (claim != null && claim.matches(player.getWorld(), stable) && EarthenDrake.stage(player) < 2
                        && !claim.stall().contains(player.getPos()) && !claim.tryingToEscape) {
                    claim.tryingToEscape = true;
                    DrakeOutpostOwnership.get(player.getServer()).markDirty();
                }
                BlindingRein.upgrade(player);
                DrakeDialogue.say(player, DrakeDialogue.escaping(player) ? "escape_caught" : "recall_leashed");
                sourceGate = stable.gateAt(player.getPos());
                if (stable.gate(targetStall).equals(sourceGate)) sourceGate = null;
            }
            return;
        }
        if (escort == null) escort = LeashEscortEvent.start(pillager, player, Vec3d.ofBottomCenter(stable.keeperPosition(targetStall)), null);
        if (escort == null || !escort.valid()) { complete = true; return; }
        if (!atStall() && returnProgress.sample(player.getWorld().getTime(), body.getPos().distanceTo(escort.route().to()))
                && recoverStall()) return;
        if (DrakeLeashing.recoveryDestination(player, pillager) != null) { move(null, false); return; }
        gates(true);
        if (sourceGate != null) {
            navigation().open(sourceGate);
            if (DrakeStableLayout.outsideGate(stable.getBoundingBox(), sourceGate, player.getBoundingBox(), .5)
                    && DrakeStableLayout.outsideGate(stable.getBoundingBox(), sourceGate, body.getBoundingBox(), .5)) {
                navigation().close(sourceGate); sourceGate = null;
            } else {
                move(null, false);
                if (body.squaredDistanceTo(player) <= 7 * 7) {
                    var exit = DrakeStableLayout.gatePoint(stable.getBoundingBox(), sourceGate, -4);
                    move(exit, true);
                }
                return;
            }
        }
        if (atStall()) {
            arrive();
            DrakeDialogue.say(player, "stable_arrived");
            return;
        }
        if (Math.abs(body.getX() - gate.x) < .2 && Math.abs(body.getZ() - gate.z) < .45
                && body.getY() >= gate.y - .2 && body.squaredDistanceTo(player) < 16) atGate = true;
        Vec3d destination = atGate ? Vec3d.ofBottomCenter(stable.keeperPosition(targetStall)).add(.5, 0, 0) : gate;
        if (body.squaredDistanceTo(player) > 7 * 7) move(null, false);
        else move(destination, atGate || body.squaredDistanceTo(gate) < 2.25);
    }

    private boolean atStall() {
        return stable.stall(targetStall).contains(player.getPos())
                && DrakeStableLayout.insideGate(stable.getBoundingBox(), stable.gate(targetStall), player.getBoundingBox(), 0)
                && player.squaredDistanceTo(Vec3d.ofCenter(stable.tie(targetStall))) <= 81;
    }

    private void arrive() {
        DrakeOutpostOwnership.capture(player, stable, targetStall);
        DrakeLeashing.attach(player, LeashKnotEntity.getOrCreate(pillager.getWorld(), stable.tie(targetStall)));
        escort.finish(); escort = null;
        returnProgress.clear();
        leaving = true; started = pillager.age;
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(ItemStack.EMPTY);
    }

    private boolean recoverStall() {
        if (!(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) return false;
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null || !claim.matches(player.getWorld(), stable) || claim.stallIndex != targetStall
                || !eligible(player) || player.hasVehicle() || player.hasPassengers() || occupied()
                || !pillager.getWorld().getBlockState(stable.tie(targetStall)).isIn(BlockTags.FENCES)) return false;
        var world = serverPlayer.getServerWorld();
        world.getChunk(claim.bed());
        var body = pillager.getRootVehicle();
        var guidePosition = DrakeStableLayout.safePosition(body, world, stable.keeperPosition(targetStall));
        if (guidePosition == null) return false;
        move(null, false);
        if (!DrakeBoundReturn.returnToStall(serverPlayer, claim)) return false;
        body.requestTeleport(guidePosition.x, guidePosition.y, guidePosition.z);
        body.setVelocity(Vec3d.ZERO); body.fallDistance = 0;
        if (body != pillager) body.updatePassengerPosition(pillager);
        pillager.fallDistance = 0;
        if (sourceGate != null) { navigation().close(sourceGate); sourceGate = null; }
        gates(true);
        arrive();
        recoveryHintAt = world.getTime() + DrakeBoundReturn.BLACKOUT_TICKS;
        return true;
    }

    private void move(Vec3d destination, boolean direct) {
        double speed = pillager.isSprinting() ? 1 : .8;
        if (escort != null && !leaving) { if (!escort.move(destination, speed, direct)) complete = true; return; }
        if (DrakeBattleGoal.of(pillager).movePursuit(player, destination, speed, direct)) return;
        if (destination == null || direct) navigation().stop();
        if (destination == null) return;
        if (direct) pillager.getMoveControl().moveTo(destination.x, destination.y, destination.z, .8);
        else navigation().startMovingAlong(navigation().findPathTo(destination.x, destination.y, destination.z, 0), speed);
    }

    @Override public void stop() {
        returnProgress.clear(); recoveryHintAt = -1;
        if (pursuit != null) { pursuit.remove(pillager); pursuit = null; }
        if (escort != null) { escort.finish(); escort = null; }
        DrakeBattleGoal.of(pillager).stopPursuit();
        if (player != null && DrakeLeashing.holder(player) == pillager) DrakeLeashing.detach(player, true);
        player = null; stable = null; sourceGate = null; targetStall = -1; nextSearch = pillager.age + 100;
        pillager.setSprinting(false);
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(ItemStack.EMPTY);
        pillager.getNavigation().stop();
    }
}
