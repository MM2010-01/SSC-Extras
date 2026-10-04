package sscextras.drake;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.CrossbowAttackGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.util.AttackEntityDataTracker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;

public final class DrakeBattleGoal extends Goal {
    public interface Rider { DrakeBattleGoal sscExtras$battleGoal(); }
    private enum Phase { APPROACH, EXIT, FIGHT, RETURN, ENTER, LEAVE }
    private final PillagerEntity pillager;
    private final CrossbowAttackGoal<PillagerEntity> attack;
    private DrakeStablePiece stable;
    private LivingEntity mount, enemy;
    private LivingEntity attacker;
    private long attackedAt;
    private BlockPos homeGate, homeTie, exitGate;
    private Phase phase;
    private int nextSearch, started, nextPath, lastSeen, hungerBefore;
    private boolean complete, fighting, won;

    public DrakeBattleGoal(PillagerEntity pillager) {
        this.pillager = pillager;
        attack = new CrossbowAttackGoal<>(pillager, 1, 8);
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    public static DrakeBattleGoal of(PillagerEntity pillager) { return ((Rider)pillager).sscExtras$battleGoal(); }
    public LivingEntity mount() { return phase == null ? null : mount; }
    public boolean returning() { return phase == Phase.RETURN || phase == Phase.ENTER || phase == Phase.LEAVE; }
    public boolean holdsOpen(BlockPos gate) {
        return phase != null && !complete && (gate.equals(exitGate) && phase == Phase.EXIT
                || gate.equals(homeGate) && (phase == Phase.ENTER || phase == Phase.LEAVE));
    }
    public static boolean riding(LivingEntity mount) {
        return mount.getFirstPassenger() instanceof PillagerEntity pillager && of(pillager) != null && of(pillager).mount() == mount;
    }
    public static boolean assigned(LivingEntity mount) {
        var pillager = ((DrakeRiding.State)mount).sscExtras$battleRider();
        return pillager != null && pillager.getWorld() == mount.getWorld() && pillager.isAlive() && !pillager.isRemoved() && of(pillager) != null && of(pillager).mount() == mount;
    }

    private boolean owned(PlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        return claim != null && claim.matches(pillager.getWorld(), stable) && !BondOfTheBeastCompat.hasOwner(player);
    }

    private boolean owned(LivingEntity entity) {
        return entity instanceof PlayerEntity player && owned(player) || entity instanceof StableDrakeEntity drake && drake.belongsTo(stable);
    }

    public static void alert(LivingEntity mount, LivingEntity attacker) {
        if (DrakeFaction.member(attacker)) return;
        for (var pillager : mount.getWorld().getEntitiesByClass(PillagerEntity.class, mount.getBoundingBox().expand(64),
                candidate -> candidate.isAlive() && !candidate.isAiDisabled())) {
            var goal = of(pillager);
            if (goal == null) continue;
            var stable = ((DrakeStableNavigation)pillager.getNavigation()).stable();
            boolean matches = stable != null && (mount instanceof PlayerEntity player && DrakeOutpostOwnership.owns(player, stable)
                    || mount instanceof StableDrakeEntity drake && drake.belongsTo(stable));
            if (matches) { goal.attacker = attacker; goal.attackedAt = mount.getWorld().getTime(); }
        }
    }

    private boolean threat(LivingEntity target) {
        if (target == null || !target.isAlive() || target.isSpectator() || target == mount || DrakeFaction.member(target)
                || target instanceof StableDrakeEntity || pillager.isTeammate(target)) return false;
        if (target instanceof PlayerEntity player) return !player.isCreative() && !owned(player)
                && (target == attacker && pillager.getWorld().getTime() - attackedAt < 1200
                || pillager.getWorld().getTime() - AttackEntityDataTracker.lastAttackPillagerTimeMap
                        .getOrDefault(player.getUuid(), -1200L) < 1200);
        return target instanceof HostileEntity || target instanceof MobEntity mob
                && mob.getTarget() != null && owned(mob.getTarget())
                || target == attacker && pillager.getWorld().getTime() - attackedAt < 1200;
    }

    private LivingEntity findEnemy() {
        if (threat(attacker) && pillager.squaredDistanceTo(attacker) <= 64 * 64) return attacker;
        if (threat(pillager.getTarget()) && pillager.squaredDistanceTo(pillager.getTarget()) <= 64 * 64) return pillager.getTarget();
        return pillager.getWorld().getEntitiesByClass(LivingEntity.class, Box.from(stable.getBoundingBox()).expand(80), target ->
                threat(target) && (pillager.squaredDistanceTo(target) <= 32 * 32
                        || target instanceof MobEntity mob && mob.getTarget() != null && owned(mob.getTarget()))
                        && pillager.getVisibilityCache().canSee(target)).stream()
                .min(Comparator.comparingDouble(pillager::squaredDistanceTo)).orElse(null);
    }

    private boolean available(LivingEntity candidate) {
        if (!candidate.isAlive() || candidate.hasPassengers() || candidate.hasVehicle()
                || !(DrakeEquipment.canRide(candidate) || candidate instanceof PlayerEntity player && DrakeRiding.canCarryPillager(player))
                || !DrakeEquipment.hasReins(candidate) || !DrakeCaptureGoal.near(stable, candidate.getPos()) || assigned(candidate)) return false;
        if (candidate instanceof PlayerEntity player) {
            var claim = DrakeOutpostOwnership.claim(player);
            var holder = DrakeLeashing.holder(player);
            return !player.isCreative() && !player.isSpectator() && owned(player)
                    && (holder == null || holder instanceof LeashKnotEntity knot && knot.getDecorationBlockPos().equals(claim.tie()));
        }
        return candidate instanceof StableDrakeEntity drake && drake.belongsTo(stable) && !drake.isLeashed();
    }

    @Override public boolean canStart() {
        if (pillager.hasVehicle() || pillager.age < nextSearch || !pillager.isHolding(net.minecraft.item.Items.CROSSBOW)) return false;
        nextSearch = pillager.age + 40;
        stable = navigation().stable();
        if (stable == null) return false;
        enemy = findEnemy();
        if (enemy == null) return false;
        var candidates = new ArrayList<LivingEntity>();
        candidates.addAll(pillager.getWorld().getEntitiesByClass(StableDrakeEntity.class,
                Box.from(stable.getBoundingBox()).expand(DrakeRoaming.RANGE), this::available));
        for (var player : pillager.getWorld().getPlayers()) if (available(player)) candidates.add(player);
        while (!candidates.isEmpty()) {
            var candidate = candidates.remove(pillager.getRandom().nextInt(candidates.size()));
            if (!navigation().reaches(candidate.getBlockPos())) continue;
            mount = candidate;
            homeGate = candidate instanceof StableDrakeEntity drake ? stable.gate(drake.homeStall()) : stable.reservedGate();
            homeTie = new BlockPos(homeGate.getX(), homeGate.getY(), stable.getBoundingBox().getMaxZ());
            if (navigation().reaches(homeTie.north(2))) return true;
        }
        mount = null; return false;
    }

    @Override public void start() {
        phase = Phase.APPROACH; complete = fighting = won = false; started = pillager.age; nextPath = 0;
        ((DrakeRiding.State)mount).sscExtras$battleRider(pillager);
        pillager.clearActiveItem(); pillager.setCharging(false); pillager.setTarget(null);
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(net.minecraft.item.ItemStack.EMPTY);
    }

    @Override public boolean shouldContinue() {
        if (complete || mount == null || !mount.isAlive() || mount.getWorld() != pillager.getWorld() || pillager.age - started > 3600) return false;
        if (mount instanceof PlayerEntity player && (!owned(player) || player.isCreative() || player.isSpectator())) return false;
        return phase == Phase.APPROACH || phase == Phase.LEAVE || pillager.getVehicle() == mount;
    }

    @Override public boolean shouldRunEveryTick() { return true; }

    @Override public void tick() {
        if (phase == Phase.APPROACH) {
            if (mount.hasPassengers() || mount.hasVehicle()) { complete = true; return; }
            pathTo(mount.getPos(), 1);
            if (pillager.squaredDistanceTo(mount) > 4 || !pillager.getVisibilityCache().canSee(mount)) return;
            if (mount instanceof PlayerEntity player) {
                if (player.isSleeping()) player.wakeUp();
                hungerBefore = player.getHungerManager().getFoodLevel();
            }
            if (!pillager.startRiding(mount)) { complete = true; return; }
            if (mount instanceof PlayerEntity player) DrakeLeashing.detach(player, false);
            exitGate = stable.gateAt(mount.getPos());
            phase = exitGate == null ? Phase.FIGHT : Phase.EXIT;
            nextPath = 0; lastSeen = pillager.age;
        }
        if (phase == Phase.EXIT) {
            navigation().open(exitGate);
            if (mount.getBoundingBox().maxZ < exitGate.getZ() - .5) {
                navigation().close(exitGate); phase = Phase.FIGHT; nextPath = 0;
            } else { navigation().stop(); return; }
        }
        if (phase == Phase.FIGHT) {
            if (!threat(enemy) || !DrakeCaptureGoal.near(stable, mount.getPos()) || pillager.age - lastSeen > 200) {
                won |= enemy != null && !enemy.isAlive();
                var next = findEnemy();
                if (won && next != null && DrakeCaptureGoal.near(stable, mount.getPos())) {
                    enemy = next; lastSeen = pillager.age;
                } else { beginReturn(); return; }
            }
            if (pillager.getVisibilityCache().canSee(enemy)) lastSeen = pillager.age;
            pillager.setTarget(enemy);
            if (!fighting) { attack.start(); fighting = true; }
            attack.tick();
            return;
        }
        if (phase == Phase.RETURN) {
            pillager.setTarget(null);
            var outside = Vec3d.ofBottomCenter(homeGate.north(3)).add(.5, 0, 0);
            pathTo(outside, .8);
            if (mount.squaredDistanceTo(outside) < 2.25) { phase = Phase.ENTER; navigation().stop(); }
            return;
        }
        if (phase == Phase.ENTER) {
            navigation().open(homeGate);
            if (mount.getBoundingBox().minZ < homeGate.getZ() + 2) return;
            pillager.stopRiding();
            ((DrakeRiding.State)mount).sscExtras$setRiderInput(null);
            if (mount instanceof PlayerEntity player) {
                DrakeLeashing.attach(player, LeashKnotEntity.getOrCreate(pillager.getWorld(), homeTie));
            } else if (mount instanceof StableDrakeEntity drake) {
                drake.getNavigation().stop(); drake.setTarget(null);
            }
            if (won && pillager.getRandom().nextFloat() < .3f) {
                if (mount instanceof PlayerEntity player)
                    DrakeFeedGoal.treat(pillager, player, Math.max(0, hungerBefore - player.getHungerManager().getFoodLevel()));
                else {
                    mount.heal(4);
                    mount.playSound(net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EAT, .7f, 1);
                    ((ServerWorld)mount.getWorld()).spawnParticles(new net.minecraft.particle.ItemStackParticleEffect(
                            net.minecraft.particle.ParticleTypes.ITEM, new net.minecraft.item.ItemStack(net.minecraft.item.Items.BEEF)),
                            mount.getX(), mount.getEyeY(), mount.getZ(), 8, .15, .1, .15, .05);
                }
            }
            phase = Phase.LEAVE; nextPath = 0;
        }
        if (phase == Phase.LEAVE) {
            pillager.setTarget(null);
            if (pillager.getBoundingBox().maxZ < homeGate.getZ() - .5) {
                navigation().close(homeGate); complete = true;
            } else pathTo(Vec3d.ofBottomCenter(homeGate.north(3)).add(.5, 0, 0), .8);
        }
    }

    private void beginReturn() {
        if (fighting) { attack.stop(); fighting = false; }
        phase = Phase.RETURN; nextPath = 0; navigation().stop();
    }

    private void pathTo(Vec3d target, double speed) {
        if (pillager.age >= nextPath) {
            navigation().startMovingTo(target.x, target.y, target.z, speed);
            nextPath = pillager.age + 10;
        }
    }

    public Vec3d directDestination() {
        if (phase == Phase.EXIT) return new Vec3d(exitGate.getX() + 1, exitGate.getY(), exitGate.getZ() - 4);
        if (phase == Phase.ENTER) return new Vec3d(homeGate.getX() + 1, homeGate.getY(), homeGate.getZ() + 5);
        return null;
    }

    private DrakeStableNavigation navigation() { return (DrakeStableNavigation)pillager.getNavigation(); }

    @Override public void stop() {
        if (fighting) attack.stop();
        if (pillager.getVehicle() == mount) pillager.stopRiding();
        if (mount != null) {
            ((DrakeRiding.State)mount).sscExtras$setRiderInput(null);
            ((DrakeRiding.State)mount).sscExtras$battleRider(null);
        }
        phase = null; mount = enemy = null; nextSearch = pillager.age + 100;
        navigation().stop();
    }
}
