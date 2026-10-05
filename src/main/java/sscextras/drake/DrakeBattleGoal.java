package sscextras.drake;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.EquipmentSlot;
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
    private enum Phase { APPROACH, EXIT, FIGHT, PATROL, RETURN, ALIGN, ENTER, LEAVE }
    private enum Purpose { BATTLE, PATROL, RECALL }
    private final PillagerEntity pillager;
    private final CrossbowAttackGoal<PillagerEntity> attack;
    private DrakeStablePiece stable;
    private LivingEntity mount, enemy;
    private LivingEntity attacker;
    private long attackedAt;
    private BlockPos homeGate, homeTie, exitGate;
    private int homeStall;
    private Phase phase;
    private Purpose purpose;
    private int nextSearch, nextPatrolSearch, started, nextPath, nextEnemySearch, lastSeen, hungerBefore, patrolUntil;
    private boolean complete, fighting, won, engaged, pendingRecall;

    public DrakeBattleGoal(PillagerEntity pillager) {
        this.pillager = pillager;
        attack = new CrossbowAttackGoal<>(pillager, 1, 8);
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    public static DrakeBattleGoal of(PillagerEntity pillager) { return ((Rider)pillager).sscExtras$battleGoal(); }
    public LivingEntity mount() { return phase == null && !pendingRecall ? null : mount; }
    public boolean reserves(DrakeStablePiece home, int stall, PlayerEntity candidate) {
        return mount() instanceof PlayerEntity player && player != candidate && stable != null
                && homeStall == stall && stable.getBoundingBox().equals(home.getBoundingBox());
    }
    public boolean returning() { return phase == Phase.RETURN || phase == Phase.ALIGN || phase == Phase.ENTER || phase == Phase.LEAVE; }
    public boolean pathing() { return returning() || phase == Phase.PATROL; }
    public boolean patrolling() { return purpose == Purpose.PATROL && phase != null; }
    public void shot(LivingEntity target) { if (purpose == Purpose.BATTLE && phase == Phase.FIGHT && target == enemy) engaged = true; }
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
        if (target == null || !target.isAlive() || target.getWorld() != pillager.getWorld() || target.isSpectator() || target == mount || DrakeFaction.member(target)
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
        var nearest = pillager.getWorld().getEntitiesByClass(LivingEntity.class, Box.from(stable.getBoundingBox()).expand(128), target ->
                threat(target) && (pillager.squaredDistanceTo(target) <= 32 * 32
                        || target instanceof MobEntity mob && mob.getTarget() != null && owned(mob.getTarget()))
                        && pillager.getVisibilityCache().canSee(target)).stream()
                .min(Comparator.comparingDouble(pillager::squaredDistanceTo)).orElse(null);
        for (var candidate : new LivingEntity[]{attacker, pillager.getTarget()})
            if (threat(candidate) && pillager.squaredDistanceTo(candidate) <= 64 * 64
                    && (candidate == attacker || pillager.getVisibilityCache().canSee(candidate))
                    && (nearest == null || pillager.squaredDistanceTo(candidate) < pillager.squaredDistanceTo(nearest))) nearest = candidate;
        return nearest;
    }

    private boolean available(LivingEntity candidate) {
        if (!candidate.isAlive() || candidate.hasPassengers() || candidate.hasVehicle()
                || !(DrakeEquipment.canRide(candidate) || candidate instanceof PlayerEntity player && DrakeRiding.canCarryPillager(player))
                || !DrakeEquipment.hasReins(candidate) || !DrakeCaptureGoal.near(stable, candidate.getPos()) || assigned(candidate) || candidate instanceof PlayerEntity player && DrakeSoulbinding.ritualActive(player)) return false;
        if (candidate instanceof PlayerEntity player) {
            var claim = DrakeOutpostOwnership.claim(player);
            var holder = DrakeLeashing.holder(player);
            return !player.isCreative() && !player.isSpectator() && !player.isSleeping() && owned(player)
                    && (holder == null || holder instanceof LeashKnotEntity knot && knot.getDecorationBlockPos().equals(claim.tie())
                        || holder == pillager && DrakeRoaming.following(pillager) == player);
        }
        return candidate instanceof StableDrakeEntity drake && drake.belongsTo(stable)
                && (!drake.isLeashed() || drake.getHoldingEntity() instanceof LeashKnotEntity knot
                    && knot.getDecorationBlockPos().equals(stable.tie(drake.homeStall())));
    }

    @Override public boolean canStart() {
        if (pendingRecall) {
            if (mount instanceof PlayerEntity player && DrakeCaptureGoal.eligible(player) && DrakeRiding.canCarryPillager(player)
                    && !mount.hasPassengers() && !mount.hasVehicle() && !pillager.hasVehicle()) return true;
            stop(); return false;
        }
        if (pillager.hasVehicle() || pillager.age < nextSearch
                || !pillager.getEquippedStack(EquipmentSlot.MAINHAND).isOf(net.minecraft.item.Items.CROSSBOW)
                    && !pillager.getEquippedStack(EquipmentSlot.OFFHAND).isOf(net.minecraft.item.Items.CROSSBOW)) return false;
        nextSearch = pillager.age + 40;
        stable = navigation().stable();
        if (stable == null) return false;
        enemy = findEnemy();
        purpose = enemy != null ? Purpose.BATTLE : Purpose.RECALL;
        if (chooseMount()) return true;
        if (enemy != null || !pillager.getWorld().isDay() || pillager.age < nextPatrolSearch) return false;
        nextPatrolSearch = pillager.age + 200;
        boolean called = pillager.getWorld().getPlayers().stream().anyMatch(player -> DrakeAttention.called(player)
                && available(player) && pillager.squaredDistanceTo(player) <= 32 * 32
                && pillager.getVisibilityCache().canSee(player));
        if (pillager.getRandom().nextInt(5) >= (called ? 3 : 1)) return false;
        purpose = Purpose.PATROL;
        return chooseMount();
    }

    private boolean chooseMount() {
        if (purpose == Purpose.BATTLE) {
            var escorted = DrakeRoaming.following(pillager);
            if (escorted != null && available(escorted) && navigation().reaches(escorted.getBlockPos())) {
                setMount(escorted);
                if (navigation().reaches(stable.keeperPosition(homeStall))) return true;
            }
        }
        var candidates = new ArrayList<LivingEntity>();
        candidates.addAll(pillager.getWorld().getEntitiesByClass(StableDrakeEntity.class,
                Box.from(stable.getBoundingBox()).expand(DrakeCaptureGoal.RANGE), this::available));
        for (var player : pillager.getWorld().getPlayers()) if (available(player)) candidates.add(player);
        while (!candidates.isEmpty()) {
            int total = candidates.stream().mapToInt(candidate -> DrakeAttention.called(candidate) ? 3 : 1).sum();
            int choice = pillager.getRandom().nextInt(total), index = 0;
            while ((choice -= DrakeAttention.called(candidates.get(index)) ? 3 : 1) >= 0) index++;
            var candidate = candidates.remove(index);
            if (purpose != Purpose.BATTLE && (!pillager.getVisibilityCache().canSee(candidate)
                    || pillager.squaredDistanceTo(candidate) > 32 * 32)) continue;
            if (purpose == Purpose.RECALL && (!(candidate instanceof StableDrakeEntity drake)
                    || pillager.getWorld().isDay() && DrakeCaptureGoal.near(stable.getBoundingBox(), candidate.getPos(), 8)
                    || !pillager.getWorld().isDay() && drake.isLeashed())) continue;
            if (purpose == Purpose.PATROL && (((DrakeRiding.State)candidate).sscExtras$nextPatrol() > pillager.getWorld().getTime()
                    || !DrakeCaptureGoal.near(stable.getBoundingBox(), candidate.getPos(), DrakeStableLayout.roamRange(stable.getBoundingBox()))
                    || candidate instanceof PlayerEntity player && DrakeOutpostOwnership.claim(player).tryingToEscape)) continue;
            if (!navigation().reaches(candidate.getBlockPos())) continue;
            setMount(candidate);
            if (navigation().reaches(stable.keeperPosition(homeStall))) return true;
        }
        mount = null; return false;
    }

    private void setMount(LivingEntity candidate) {
        mount = candidate;
        homeStall = candidate instanceof StableDrakeEntity drake ? drake.homeStall()
                : DrakeOutpostOwnership.availableStall((ServerWorld)pillager.getWorld(), stable, (PlayerEntity)candidate);
        homeGate = stable.gate(homeStall);
        homeTie = stable.tie(homeStall);
    }

    public boolean recall(PlayerEntity player, DrakeStablePiece home) {
        if (phase != null || pendingRecall || player.hasPassengers() || player.hasVehicle()
                || !DrakeRiding.canCarryPillager(player) || assigned(player)) return false;
        if (DrakeOutpostOwnership.availableStall((ServerWorld)pillager.getWorld(), home, player) < 0) return false;
        stable = home; purpose = Purpose.RECALL; enemy = null;
        setMount(player); pendingRecall = true;
        ((DrakeRiding.State)player).sscExtras$battleRider(pillager);
        return true;
    }

    @Override public void start() {
        phase = Phase.APPROACH; complete = fighting = won = engaged = pendingRecall = false; started = pillager.age; nextPath = 0;
        patrolUntil = 0; nextEnemySearch = 0;
        ((DrakeRiding.State)mount).sscExtras$battleRider(pillager);
        pillager.clearActiveItem(); pillager.setCharging(false); pillager.setTarget(null);
        ((DrakeFaction.EquipmentDisplay)pillager).sscExtras$showEquipment(net.minecraft.item.ItemStack.EMPTY);
        DrakeDialogue.say(mount, purpose == Purpose.BATTLE ? "mount_battle" : purpose == Purpose.RECALL ? "mount_recall"
                : (pillager.getId() & 1) == 0 ? "mount_guard" : "mount_roam");
    }

    @Override public boolean shouldContinue() {
        if (complete || mount == null || !mount.isAlive() || mount.getWorld() != pillager.getWorld() || pillager.age - started > 3600) return false;
        if (mount instanceof PlayerEntity player && (purpose != Purpose.RECALL && !owned(player)
                || player.isCreative() || player.isSpectator() || player.isSleeping()
                || !DrakeRiding.canCarryPillager(player)
                || purpose == Purpose.RECALL && (!DrakeCaptureGoal.eligible(player) || !DrakeOutpostOwnership.available((ServerWorld)pillager.getWorld(), stable, homeStall, player)))) return false;
        return phase == Phase.APPROACH || phase == Phase.LEAVE || pillager.getVehicle() == mount;
    }

    @Override public boolean shouldRunEveryTick() { return true; }

    @Override public void tick() {
        if (purpose == Purpose.BATTLE && (phase == Phase.APPROACH || phase == Phase.EXIT || phase == Phase.FIGHT)
                && (!threat(enemy) || pillager.age >= nextEnemySearch)) {
            nextEnemySearch = pillager.age + 10;
            boolean invalid = !threat(enemy);
            won |= invalid && engaged && enemy != null && !enemy.isAlive();
            var next = findEnemy();
            if (next != null && (invalid || next != enemy && pillager.squaredDistanceTo(next) < pillager.squaredDistanceTo(enemy) * .64))
                selectEnemy(next);
            else if (invalid) {
                if (engaged || won) beginReturn();
                else { complete = true; navigation().stop(); pillager.setTarget(null); }
                return;
            }
        }
        if (phase == Phase.APPROACH) {
            if (mount.hasPassengers() || mount.hasVehicle() || mount instanceof PlayerEntity player && player.isSleeping()) {
                complete = true; return;
            }
            pathTo(mount.getPos(), 1);
            if (pillager.squaredDistanceTo(mount) > 4 || !pillager.getVisibilityCache().canSee(mount)) return;
            if (purpose == Purpose.RECALL && mount instanceof StableDrakeEntity drake && stable.stall(homeStall).contains(drake.getPos())) {
                tetherResident(drake);
                phase = Phase.LEAVE; nextPath = 0;
                return;
            }
            if (mount instanceof PlayerEntity player) {
                hungerBefore = player.getHungerManager().getFoodLevel();
            }
            if (!pillager.startRiding(mount)) { complete = true; return; }
            if (mount instanceof PlayerEntity player) {
                BlindingRein.upgrade(player);
                if (purpose == Purpose.BATTLE) BlindingRein.setClosed(player, true);
                DrakeLeashing.detach(player, false);
            } else if (mount instanceof StableDrakeEntity drake) drake.detachLeash(true, false);
            exitGate = stable.gateAt(mount.getPos());
            phase = exitGate == null ? afterExit() : Phase.EXIT;
            nextPath = 0; lastSeen = pillager.age;
        }
        if (phase == Phase.EXIT) {
            navigation().open(exitGate);
            if (DrakeStableLayout.outsideGate(stable.getBoundingBox(), exitGate, mount.getBoundingBox(), .5)) {
                navigation().close(exitGate); phase = afterExit(); nextPath = 0;
            } else { navigation().stop(); return; }
        }
        if (phase == Phase.FIGHT) {
            if (!DrakeCaptureGoal.near(stable, mount.getPos()) || pillager.age - lastSeen > 200) {
                var next = findEnemy();
                if (next != null && next != enemy && DrakeCaptureGoal.near(stable, mount.getPos())) selectEnemy(next);
                else { beginReturn(); return; }
            }
            if (pillager.getVisibilityCache().canSee(enemy)) lastSeen = pillager.age;
            pillager.setTarget(enemy);
            if (!fighting) { attack.start(); fighting = true; }
            attack.tick();
            return;
        }
        if (phase == Phase.PATROL) {
            var threat = findEnemy();
            if (threat != null) {
                purpose = Purpose.BATTLE; enemy = threat; engaged = false; phase = Phase.FIGHT; lastSeen = pillager.age;
                if (mount instanceof PlayerEntity player) BlindingRein.setClosed(player, true);
                DrakeDialogue.say(mount, "battle_spotted"); return;
            }
            if (!pillager.getWorld().isDay() || pillager.age >= patrolUntil
                    || !DrakeCaptureGoal.near(stable.getBoundingBox(), mount.getPos(), DrakeStableLayout.roamRange(stable.getBoundingBox()))) { beginReturn(); return; }
            if (navigation().isIdle() && pillager.age >= nextPath) {
                nextPath = pillager.age + 40;
                var route = DrakeGuardGoal.patrolPath(pillager, stable);
                if (route != null) navigation().startMovingAlong(route, 1);
            }
            return;
        }
        if (phase == Phase.RETURN) {
            pillager.setTarget(null);
            var outside = DrakeStableLayout.gatePoint(stable.getBoundingBox(), homeGate, -2.5);
            pathTo(outside, 1);
            if (mount.squaredDistanceTo(outside) >= 2.25) return;
            phase = Phase.ALIGN; navigation().stop();
        }
        if (phase == Phase.ALIGN) {
            if (Math.abs(mount.getX() - homeGate.getX() - 1) < .2
                    && Math.abs(mount.getZ() - DrakeStableLayout.gatePoint(stable.getBoundingBox(), homeGate, -2.5).z) < .45 && mount.getY() >= homeGate.getY() - .2)
                phase = Phase.ENTER;
            else return;
        }
        if (phase == Phase.ENTER) {
            navigation().open(homeGate);
            if (!DrakeStableLayout.insideGate(stable.getBoundingBox(), homeGate, mount.getBoundingBox(), 1)) return;
            pillager.stopRiding();
            ((DrakeRiding.State)mount).sscExtras$setRiderInput(null);
            if (mount instanceof PlayerEntity player) {
                if (purpose == Purpose.RECALL) DrakeOutpostOwnership.capture(player, stable, homeStall);
                BlindingRein.setClosed(player, false);
                DrakeLeashing.attachPillager(player, LeashKnotEntity.getOrCreate(pillager.getWorld(), homeTie));
                DrakeDialogue.say(player, "stable_arrived");
            } else if (mount instanceof StableDrakeEntity drake) {
                tetherResident(drake);
            }
            if (purpose == Purpose.BATTLE && won && pillager.getRandom().nextFloat() < .3f) {
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
            if (DrakeStableLayout.outsideGate(stable.getBoundingBox(), homeGate, pillager.getBoundingBox(), .5)) {
                navigation().close(homeGate); complete = true;
            } else pathTo(DrakeStableLayout.gatePoint(stable.getBoundingBox(), homeGate, -2.5), .8);
        }
    }

    private void tetherResident(StableDrakeEntity drake) {
        drake.getNavigation().stop(); drake.setTarget(null);
        drake.attachLeash(LeashKnotEntity.getOrCreate(pillager.getWorld(), homeTie), true);
    }

    private Phase afterExit() {
        if (purpose == Purpose.PATROL) { patrolUntil = pillager.age + 400 + pillager.getRandom().nextInt(401); return Phase.PATROL; }
        return purpose == Purpose.RECALL ? Phase.RETURN : Phase.FIGHT;
    }

    private void selectEnemy(LivingEntity next) {
        if (fighting) { attack.stop(); fighting = false; }
        navigation().stop();
        enemy = next; engaged = false; lastSeen = pillager.age;
    }

    private void beginReturn() {
        if (fighting) { attack.stop(); fighting = false; }
        phase = Phase.RETURN; nextPath = 0; navigation().stop();
    }

    private void pathTo(Vec3d target, double speed) {
        if (pillager.age >= nextPath) {
            navigation().startMovingAlong(navigation().findPathTo(target.x, target.y, target.z, phase == Phase.RETURN ? 0 : 1), speed);
            nextPath = pillager.age + 10;
        }
    }

    public Vec3d directDestination() {
        if (phase == Phase.ALIGN) return DrakeStableLayout.gatePoint(stable.getBoundingBox(), homeGate, -2.5);
        if (phase == Phase.EXIT) return DrakeStableLayout.gatePoint(stable.getBoundingBox(), exitGate, -4);
        if (phase == Phase.ENTER) return DrakeStableLayout.gatePoint(stable.getBoundingBox(), homeGate, 5);
        return null;
    }

    private DrakeStableNavigation navigation() { return (DrakeStableNavigation)pillager.getNavigation(); }

    @Override public void stop() {
        if (fighting) attack.stop();
        if (pillager.getVehicle() == mount) pillager.stopRiding();
        if (mount != null) {
            mount.setSprinting(false);
            ((DrakeRiding.State)mount).sscExtras$nextPatrol(pillager.getWorld().getTime() + 1200);
            ((DrakeRiding.State)mount).sscExtras$setRiderInput(null);
            ((DrakeRiding.State)mount).sscExtras$battleRider(null);
            if (mount instanceof PlayerEntity player && !DrakeRiding.canCarryPillager(player))
                ((DrakeCaptureGoal.Captor)pillager).sscExtras$captureGoal().recallAfterDismount();
        }
        phase = null; pendingRecall = false; mount = enemy = null; nextSearch = pillager.age + 100;
        navigation().stop();
    }
}
