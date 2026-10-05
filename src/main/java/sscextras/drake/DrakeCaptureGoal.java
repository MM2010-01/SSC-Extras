package sscextras.drake;

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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;

public final class DrakeCaptureGoal extends Goal {
    public static final int RANGE = 128;
    public interface Captor { DrakeCaptureGoal sscExtras$captureGoal(); }
    private final PillagerEntity pillager;
    private DrakeStablePiece stable;
    private PlayerEntity player;
    private BlockPos sourceGate;
    private int nextSearch, started, nextDisplay;
    private int targetStall = -1;
    private boolean atGate, leaving, complete;

    public DrakeCaptureGoal(PillagerEntity pillager) {
        this.pillager = pillager;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    public static boolean eligible(PlayerEntity player) {
        return DrakeLeashing.eligible(player) && !player.isCreative() && !player.hasVehicle() && !BondOfTheBeastCompat.hasOwner(player)
                && (DrakeOutpostOwnership.claim(player) != null || cursedHarness(player));
    }

    private static boolean cursedHarness(PlayerEntity player) {
        return !DrakeEquipment.equipped(player, DrakeEquipment.REINS).isEmpty()
                && !DrakeEquipment.equipped(player, DrakeEquipment.SADDLE).isEmpty();
    }

    private boolean wants(PlayerEntity player) {
        if (!eligible(player) || DrakeBattleGoal.assigned(player) || DrakeSoulbinding.ritualActive(player)) return false;
        if (!DrakeOutpostOwnership.owns(player, stable)) return cursedHarness(player);
        var claim = DrakeOutpostOwnership.claim(player);
        return !pillager.getWorld().isDay() || EarthenDrake.stage(player) < 0
                || claim.tryingToEscape && (DrakeLeashing.holder(player) == pillager
                    || pillager.getWorld().getTime() < claim.recallUntil || DrakeRoaming.canSee(pillager, player));
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
        var outpost = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(new Identifier("minecraft", "pillager_outpost"));
        var mansion = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(new Identifier("minecraft", "mansion"));
        var checked = new HashSet<net.minecraft.structure.StructureStart>();
        var chunk = new ChunkPos(pos);
        DrakeStablePiece closest = null;
        double distance = Double.MAX_VALUE;
        int chunks = RANGE / 16 + 1;
        for (int x = chunk.x - chunks; x <= chunk.x + chunks; x++) for (int z = chunk.z - chunks; z <= chunk.z + chunks; z++) {
            if (!world.isChunkLoaded(x, z)) continue;
            for (var start : world.getStructureAccessor().getStructureStarts(new ChunkPos(x, z), structure -> structure == outpost || structure == mansion)) {
                if (!checked.add(start)) continue;
                for (var piece : start.getChildren()) if (piece instanceof DrakeStablePiece candidate && near(candidate, Vec3d.ofCenter(pos))) {
                    double next = candidate.getBoundingBox().getCenter().getSquaredDistance(pos);
                    if (next < distance) { closest = candidate; distance = next; }
                }
            }
        }
        return closest;
    }

    private boolean occupied() {
        if (!DrakeOutpostOwnership.available((ServerWorld)pillager.getWorld(), stable, targetStall, player)) return true;
        if (!pillager.getWorld().getEntitiesByClass(LivingEntity.class, stable.stall(targetStall),
                entity -> entity.isAlive() && !(entity instanceof PillagerEntity) && entity != player && !entity.isSpectator()).isEmpty()) return true;
        return pillager.getWorld().getEntitiesByClass(PillagerEntity.class, Box.from(stable.getBoundingBox()).expand(RANGE), entity -> {
            var other = ((Captor)entity).sscExtras$captureGoal();
            return entity != pillager && other != null && other.player != null && other.player != player && other.stable != null
                    && other.stable.getBoundingBox().equals(stable.getBoundingBox()) && other.targetStall == targetStall;
        }).size() > 0;
    }

    @Override public boolean canStart() {
        if (pillager.hasVehicle() || pillager.age < nextSearch || DrakeFaction.fighting(pillager)) return false;
        nextSearch = pillager.age + 40;
        if (pillager.getWorld().getPlayers().stream().noneMatch(candidate -> eligible(candidate)
                && pillager.squaredDistanceTo(candidate) <= (RANGE * 2 + 24) * (RANGE * 2 + 24))) return false;
        stable = navigation().stable();
        if (stable == null) return false;
        var candidates = pillager.getWorld().getEntitiesByClass(PlayerEntity.class, Box.from(stable.getBoundingBox()).expand(RANGE), candidate ->
                wants(candidate) && near(stable, candidate.getPos())
                && (!DrakeLeashing.attached(candidate) || DrakeLeashing.holder(candidate) == pillager));
        candidates.sort(Comparator.comparingDouble(pillager::squaredDistanceTo));
        for (var candidate : candidates) {
            player = candidate;
            targetStall = DrakeOutpostOwnership.availableStall((ServerWorld)pillager.getWorld(), stable, player);
            if (targetStall < 0 || !pillager.getWorld().getBlockState(stable.tie(targetStall)).isIn(BlockTags.FENCES)) continue;
            if (DrakeLeashing.holder(player) != pillager && DrakeOutpostOwnership.owns(player, stable)
                    && stable.stall(targetStall).contains(player.getPos())) continue;
            if (!occupied() && navigation().reaches(player.getBlockPos()) && navigation().reaches(stable.keeperPosition(targetStall))) return true;
        }
        player = null; targetStall = -1;
        return false;
    }

    @Override public boolean shouldContinue() {
        if (complete || player == null || pillager.hasVehicle()) return false;
        if (leaving) return player.isAlive() && player.getWorld() == pillager.getWorld();
        return (!DrakeFaction.fighting(pillager) || DrakeLeashing.holder(player) == pillager) && wants(player)
                && near(stable, player.getPos()) && !occupied()
                && (!DrakeLeashing.attached(player) || DrakeLeashing.holder(player) == pillager)
                && pillager.getWorld().getBlockState(stable.tie(targetStall)).isIn(BlockTags.FENCES);
    }

    @Override public void start() {
        atGate = false; leaving = false; complete = false; sourceGate = null; started = pillager.age;
        gates(true);
        pillager.clearActiveItem(); pillager.setCharging(false); pillager.setTarget(null);
        nextDisplay = 0;
    }

    private void gates(boolean open) {
        if (open) navigation().open(stable.gate(targetStall));
        else navigation().close(stable.gate(targetStall));
    }

    private DrakeStableNavigation navigation() { return (DrakeStableNavigation)pillager.getNavigation(); }

    public PlayerEntity quarry() { return player; }

    public void recallAfterDismount() { nextSearch = pillager.age; }

    public boolean reserves(DrakeStablePiece home, int stall, PlayerEntity candidate) {
        return player != null && player != candidate && stable != null && targetStall == stall
                && stable.getBoundingBox().equals(home.getBoundingBox());
    }

    private boolean nearestCaptor() {
        if (DrakeLeashing.holder(player) == pillager) return true;
        double distance = pillager.squaredDistanceTo(player);
        return pillager.getWorld().getEntitiesByClass(PillagerEntity.class, player.getBoundingBox().expand(RANGE), candidate -> {
            var goal = ((Captor)candidate).sscExtras$captureGoal();
            if (!candidate.isAlive() || candidate == pillager || goal == null || goal.player != player) return false;
            double other = candidate.squaredDistanceTo(player);
            return other < distance || other == distance && candidate.getId() < pillager.getId();
        }).isEmpty();
    }

    public boolean holdsOpen(BlockPos gate) {
        return player != null && stable != null && !complete
                && (gate.equals(stable.gate(targetStall)) || gate.equals(sourceGate));
    }

    @Override public void tick() {
        if (!leaving && DrakeLeashing.attached(player) && DrakeLeashing.holder(player) != pillager) return;
        pillager.getLookControl().lookAt(player, 30, 30);
        Vec3d gate = DrakeStableLayout.gatePoint(stable.getBoundingBox(), stable.gate(targetStall), -1.5);
        pillager.setSprinting(!leaving && !atGate && sourceGate == null && pillager.squaredDistanceTo(gate) > 100
                && (DrakeLeashing.holder(player) != pillager || pillager.squaredDistanceTo(player) < 36));
        if (pillager.age >= nextDisplay) {
            nextDisplay = pillager.age + 10;
            ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(!leaving && nearestCaptor()
                    ? new ItemStack(Items.LEAD) : ItemStack.EMPTY);
        }
        if (leaving) {
            if (DrakeStableLayout.outsideGate(stable.getBoundingBox(), stable.gate(targetStall), pillager.getBoundingBox(), .25)) {
                gates(false);
                complete = true; pillager.getNavigation().stop();
            } else pillager.getNavigation().startMovingTo(gate.x, gate.y, gate.z - DrakeStableLayout.inward(stable.getBoundingBox(), stable.gate(targetStall)), .8);
            return;
        }
        if (DrakeLeashing.holder(player) != pillager) {
            pillager.getNavigation().startMovingTo(player, 1);
            if (pillager.squaredDistanceTo(player) <= 4 && pillager.getVisibilityCache().canSee(player)
                    && navigation().reaches(stable.keeperPosition(targetStall))) {
                if (DrakeRiding.canCarryPillager(player) && pillager.getRandom().nextBoolean() && DrakeBattleGoal.of(pillager).recall(player, stable)) {
                    complete = true; return;
                }
                if (!DrakeLeashing.attachPillager(player, pillager)) return;
                BlindingRein.upgrade(player);
                DrakeDialogue.say(player, DrakeDialogue.escaping(player) ? "escape_caught" : "recall_leashed");
                sourceGate = stable.gateAt(player.getPos());
                if (stable.gate(targetStall).equals(sourceGate)) sourceGate = null;
            }
            return;
        }
        gates(true);
        if (sourceGate != null) {
            navigation().open(sourceGate);
            if (DrakeStableLayout.outsideGate(stable.getBoundingBox(), sourceGate, player.getBoundingBox(), .5)
                    && DrakeStableLayout.outsideGate(stable.getBoundingBox(), sourceGate, pillager.getBoundingBox(), .5)) {
                navigation().close(sourceGate); sourceGate = null;
            } else {
                pillager.getNavigation().stop();
                if (pillager.squaredDistanceTo(player) <= 7 * 7) {
                    var exit = DrakeStableLayout.gatePoint(stable.getBoundingBox(), sourceGate, -4);
                    pillager.getMoveControl().moveTo(exit.x, exit.y, exit.z, .8);
                }
                return;
            }
        }
        if (stable.stall(targetStall).contains(player.getPos()) && DrakeStableLayout.insideGate(stable.getBoundingBox(), stable.gate(targetStall), player.getBoundingBox(), 0)
                && player.squaredDistanceTo(Vec3d.ofCenter(stable.tie(targetStall))) <= 81) {
            DrakeOutpostOwnership.capture(player, stable, targetStall);
            DrakeLeashing.attach(player, LeashKnotEntity.getOrCreate(pillager.getWorld(), stable.tie(targetStall)));
            DrakeDialogue.say(player, "stable_arrived");
            leaving = true; started = pillager.age;
            ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(ItemStack.EMPTY);
            return;
        }
        if (Math.abs(pillager.getX() - gate.x) < .2 && Math.abs(pillager.getZ() - gate.z) < .45
                && pillager.getY() >= gate.y - .2 && pillager.squaredDistanceTo(player) < 16) atGate = true;
        Vec3d destination = atGate ? Vec3d.ofBottomCenter(stable.keeperPosition(targetStall)).add(.5, 0, 0) : gate;
        if (pillager.squaredDistanceTo(player) > 7 * 7) pillager.getNavigation().stop();
        else if (atGate || pillager.squaredDistanceTo(gate) < 2.25) {
            pillager.getNavigation().stop();
            pillager.getMoveControl().moveTo(destination.x, destination.y, destination.z, .8);
        }
        else navigation().startMovingAlong(navigation().findPathTo(destination.x, destination.y, destination.z, 0), pillager.isSprinting() ? 1 : .8);
    }

    @Override public void stop() {
        if (player != null && DrakeLeashing.holder(player) == pillager) DrakeLeashing.detach(player, true);
        player = null; stable = null; sourceGate = null; targetStall = -1; nextSearch = pillager.age + 100;
        pillager.setSprinting(false);
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(ItemStack.EMPTY);
        pillager.getNavigation().stop();
    }
}
