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
        if (!eligible(player) || DrakeBattleGoal.assigned(player)) return false;
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
        return near(box, position, RANGE);
    }

    public static boolean near(net.minecraft.util.math.BlockBox box, Vec3d position, int range) {
        double dx = Math.max(Math.max(box.getMinX() - position.x, position.x - (box.getMaxX() + 1)), 0);
        double dz = Math.max(Math.max(box.getMinZ() - position.z, position.z - (box.getMaxZ() + 1)), 0);
        return dx * dx + dz * dz < range * range;
    }

    public static Box stall(DrakeStablePiece piece) {
        var center = piece.reservedStall();
        return new Box(center.getX() - 2, center.getY(), center.getZ() - 4,
                center.getX() + 4, center.getY() + 4, center.getZ() + 4);
    }

    public static DrakeStablePiece findStable(ServerWorld world, BlockPos pos) {
        var outpost = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(new Identifier("minecraft", "pillager_outpost"));
        if (outpost == null) return null;
        var checked = new HashSet<net.minecraft.structure.StructureStart>();
        var chunk = new ChunkPos(pos);
        DrakeStablePiece closest = null;
        double distance = Double.MAX_VALUE;
        int chunks = RANGE / 16 + 1;
        for (int x = chunk.x - chunks; x <= chunk.x + chunks; x++) for (int z = chunk.z - chunks; z <= chunk.z + chunks; z++) {
            if (!world.isChunkLoaded(x, z)) continue;
            for (var start : world.getStructureAccessor().getStructureStarts(new ChunkPos(x, z), structure -> structure == outpost)) {
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
        if (DrakeOutpostOwnership.reservedForAnother((ServerWorld)pillager.getWorld(), stable, player)) return true;
        if (!pillager.getWorld().getEntitiesByClass(LivingEntity.class, stall(stable),
                entity -> entity.isAlive() && !(entity instanceof PillagerEntity) && entity != player && !entity.isSpectator()).isEmpty()) return true;
        return pillager.getWorld().getEntitiesByClass(PillagerEntity.class, Box.from(stable.getBoundingBox()).expand(RANGE), entity -> {
            var other = ((Captor)entity).sscExtras$captureGoal();
            return entity != pillager && other != null && other.player != null && other.player != player && other.stable != null
                    && other.stable.reservedStall().equals(stable.reservedStall());
        }).size() > 0;
    }

    @Override public boolean canStart() {
        if (pillager.hasVehicle() || pillager.age < nextSearch || DrakeFaction.fighting(pillager)) return false;
        nextSearch = pillager.age + 40;
        if (pillager.getWorld().getPlayers().stream().noneMatch(candidate -> eligible(candidate)
                && pillager.squaredDistanceTo(candidate) <= (RANGE * 2 + 24) * (RANGE * 2 + 24))) return false;
        stable = navigation().stable();
        if (stable == null || stable.reservedStall() == null
                || !pillager.getWorld().getBlockState(stable.reservedTie()).isIn(BlockTags.FENCES)) return false;
        player = pillager.getWorld().getEntitiesByClass(PlayerEntity.class, Box.from(stable.getBoundingBox()).expand(RANGE), candidate ->
                wants(candidate) && near(stable, candidate.getPos()) && (DrakeLeashing.holder(candidate) == pillager
                        || !DrakeOutpostOwnership.owns(candidate, stable) || !stall(stable).contains(candidate.getPos()))
                && (!DrakeLeashing.attached(candidate) || DrakeLeashing.holder(candidate) == pillager)).stream()
                .sorted(Comparator.comparingDouble(pillager::squaredDistanceTo))
                .filter(candidate -> navigation().reaches(candidate.getBlockPos())).findFirst().orElse(null);
        if (player != null && !occupied() && navigation().reaches(stable.reservedTie().north(2))) return true;
        player = null;
        return false;
    }

    @Override public boolean shouldContinue() {
        if (complete || player == null || pillager.hasVehicle()) return false;
        if (leaving) return player.isAlive() && player.getWorld() == pillager.getWorld();
        return (!DrakeFaction.fighting(pillager) || DrakeLeashing.holder(player) == pillager) && wants(player)
                && near(stable, player.getPos()) && !occupied()
                && (!DrakeLeashing.attached(player) || DrakeLeashing.holder(player) == pillager)
                && pillager.getWorld().getBlockState(stable.reservedTie()).isIn(BlockTags.FENCES);
    }

    @Override public void start() {
        atGate = false; leaving = false; complete = false; sourceGate = null; started = pillager.age;
        gates(true);
        pillager.clearActiveItem(); pillager.setCharging(false); pillager.setTarget(null);
        nextDisplay = 0;
    }

    private void gates(boolean open) {
        if (open) navigation().open(stable.reservedGate());
        else navigation().close(stable.reservedGate());
    }

    private DrakeStableNavigation navigation() { return (DrakeStableNavigation)pillager.getNavigation(); }

    public PlayerEntity quarry() { return player; }

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
                && (gate.equals(stable.reservedGate()) || gate.equals(sourceGate));
    }

    @Override public void tick() {
        pillager.getLookControl().lookAt(player, 30, 30);
        Vec3d gate = Vec3d.ofBottomCenter(stable.reservedGate().north(2)).add(.5, 0, 0);
        pillager.setSprinting(!leaving && !atGate && sourceGate == null && pillager.squaredDistanceTo(gate) > 100
                && (DrakeLeashing.holder(player) != pillager || pillager.squaredDistanceTo(player) < 36));
        if (pillager.age >= nextDisplay) {
            nextDisplay = pillager.age + 10;
            ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(!leaving && nearestCaptor()
                    ? new ItemStack(Items.LEAD) : ItemStack.EMPTY);
        }
        if (leaving) {
            if (pillager.getBoundingBox().maxZ < stable.reservedGate().getZ() - .25) {
                gates(false);
                complete = true; pillager.getNavigation().stop();
            } else pillager.getNavigation().startMovingTo(gate.x, gate.y, gate.z - 1, .8);
            return;
        }
        if (DrakeLeashing.holder(player) != pillager) {
            pillager.getNavigation().startMovingTo(player, 1);
            if (pillager.squaredDistanceTo(player) <= 4 && pillager.getVisibilityCache().canSee(player)
                    && navigation().reaches(stable.reservedTie().north(2))) {
                if (DrakeRiding.canCarryPillager(player) && pillager.getRandom().nextBoolean() && DrakeBattleGoal.of(pillager).recall(player, stable)) {
                    complete = true; return;
                }
                if (!DrakeLeashing.attachPillager(player, pillager)) return;
                BlindingRein.upgrade(player);
                DrakeDialogue.say(player, DrakeDialogue.escaping(player) ? "escape_caught" : "recall_leashed");
                sourceGate = stable.gateAt(player.getPos());
                if (stable.reservedGate().equals(sourceGate)) sourceGate = null;
            }
            return;
        }
        gates(true);
        if (sourceGate != null) {
            navigation().open(sourceGate);
            if (player.getBoundingBox().maxZ < sourceGate.getZ() - .5
                    && pillager.getBoundingBox().maxZ < sourceGate.getZ() - .5) {
                navigation().close(sourceGate); sourceGate = null;
            } else {
                pillager.getNavigation().stop();
                if (pillager.squaredDistanceTo(player) <= 7 * 7)
                    pillager.getMoveControl().moveTo(sourceGate.getX() + 1, sourceGate.getY(), sourceGate.getZ() - 4, .8);
                return;
            }
        }
        if (stall(stable).contains(player.getPos()) && player.getBoundingBox().minZ >= stable.reservedGate().getZ() + 1
                && player.squaredDistanceTo(Vec3d.ofCenter(stable.reservedTie())) <= 81) {
            DrakeOutpostOwnership.capture(player, stable);
            DrakeLeashing.attach(player, LeashKnotEntity.getOrCreate(pillager.getWorld(), stable.reservedTie()));
            DrakeDialogue.say(player, "stable_arrived");
            leaving = true; started = pillager.age;
            ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(ItemStack.EMPTY);
            return;
        }
        if (pillager.squaredDistanceTo(gate) < 2.25 && pillager.squaredDistanceTo(player) < 16) atGate = true;
        Vec3d destination = atGate ? Vec3d.ofBottomCenter(stable.reservedTie().north(2)).add(.5, 0, 0) : gate;
        if (pillager.squaredDistanceTo(player) > 7 * 7) pillager.getNavigation().stop();
        else if (atGate) {
            pillager.getNavigation().stop();
            pillager.getMoveControl().moveTo(destination.x, destination.y, destination.z, .8);
        }
        else navigation().startMovingAlong(navigation().findPathTo(destination.x, destination.y, destination.z, 0), pillager.isSprinting() ? 1 : .8);
    }

    @Override public void stop() {
        if (player != null && DrakeLeashing.holder(player) == pillager) DrakeLeashing.detach(player, true);
        player = null; stable = null; sourceGate = null; nextSearch = pillager.age + 100;
        pillager.setSprinting(false);
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(ItemStack.EMPTY);
        pillager.getNavigation().stop();
    }
}
