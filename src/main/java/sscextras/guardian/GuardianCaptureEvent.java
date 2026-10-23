package sscextras.guardian;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import sscextras.drake.DrakeOutpostOwnership;
import sscextras.events.AssignedNpcEvent;
import sscextras.player.PlayerBrain;
import sscextras.player.PlayerControl;
import java.util.Comparator;
import java.util.List;

/** Transient, exclusive capture. An interruption releases control; only completed equipment/binding persists. */
public final class GuardianCaptureEvent extends AssignedNpcEvent {
    public static final int PEDESTAL_HOLD_TICKS = 100;
    private final GuardianShellEntity guardian;
    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final BlockPos pedestal;
    private PlayerControl.Handle control;
    private VillagerEntity worker;
    private int ticks, fittingTicks, heldTicks;
    private boolean arrived;
    private long lastStruggle = Long.MIN_VALUE;

    private GuardianCaptureEvent(GuardianShellEntity guardian, ServerPlayerEntity player, BlockPos pedestal) {
        this.guardian = guardian; this.player = player; world = player.getServerWorld(); this.pedestal = pedestal;
    }
    static GuardianCaptureEvent start(GuardianShellEntity guardian, ServerPlayerEntity player) {
        if (!guardian.powered() || guardian.core.isEmpty() || guardian.recharging() || guardian.energy < 20 || !PlayerControl.available(player) || PlayerControl.active(player) || !GuardianEquipment.hasSlots(player)) return null;
        var world = player.getServerWorld();
        var candidates = GuardianPedestal.nearby(world, guardian.home(), 128);
        candidates.sort(Comparator.comparingDouble(pos -> player.squaredDistanceTo(GuardianPedestal.position(pos))));
        var assignments = DrakeOutpostOwnership.get(world.getServer()).assignments();
        for (var pos : candidates) {
            if (!GuardianPedestal.vacant(world, pos, player, null)) continue;
            if (!guardian.routes.reachable(GuardianPedestal.position(pos).add(0, .5, 0))) continue;
            var event = new GuardianCaptureEvent(guardian, player, pos);
            if (event.reserve(assignments, List.of(guardian.getUuid(), player.getUuid(), GuardianPedestal.reservation(world, pos)))) {
                event.resume(); return event;
            }
        }
        return null;
    }
    void tick() {
        if (!guardian.isAlive() || guardian.isRemoved() || !player.isAlive() || player.isCreative() || player.isSpectator()
                || player.isDisconnected() || guardian.getWorld() != world || player.getWorld() != world || ++ticks > 1200
                || !owns(player.getUuid()) || !owns(guardian.getUuid()) || !GuardianPedestal.vacant(world, pedestal, player, this)) {
            finish(); return;
        }
        if (control == null) {
            if (ticks % 10 == 1) guardian.routes.moveTo(player.getPos(), 1.15, false);
            guardian.getLookControl().lookAt(player, 30, 30);
            if (guardian.squaredDistanceTo(player) > 4 || !guardian.canSee(player)) return;
            control = PlayerControl.tryStart(player, GuardianVillageMobs.STRUGGLE, () -> new Hold(), 0);
            if (control == null) { finish(); return; }
            guardian.grabbing(true);
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.captured"), false);
        }
        if (!control.active()) { finish(); return; }
        Vec3d center = GuardianPedestal.position(pedestal);
        if (!arrived) {
            if (ticks % 10 == 1 || !guardian.flying()) guardian.routes.moveTo(center.add(0, .5, 0), .8, false);
            if (!placeOnPedestal()) return;
        }
        heldTicks++;
        var offering = (GuardianOffering)world.getBlockEntity(pedestal);
        if (offering.available()) {
            if (heldTicks <= PEDESTAL_HOLD_TICKS - GuardianOffering.DURATION) return;
            offering.captureFlight(player);
        }
        else {
            if (!attendantReady()) { fittingTicks = 0; return; }
            GuardianRitual.prop(worker, fittingTicks < 20 ? GuardianEquipment.CUFFS : GuardianEquipment.COLLAR);
            if (fittingTicks % 20 == 0) worker.swingHand(Hand.MAIN_HAND);
        }
        if (++fittingTicks < (offering.available() ? GuardianOffering.DURATION : 60) || heldTicks < PEDESTAL_HOLD_TICKS) return;
        // Fitting uses the same accessory/curse path as the voluntary offering.
        if (GuardianEquipment.fitCaptured(player, pedestal)) {
            if (offering.available()) offering.consumeSet();
            GuardianRitual.sound(world, center, net.minecraft.sound.SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, .65f);
            guardian.offender = null; guardian.alertUntil = 0;
        }
        player.setPosition(center); PlayerControl.syncPosition(player);
        finish();
    }
    private boolean placeOnPedestal() {
        if (arrived) return true;
        var center = GuardianPedestal.position(pedestal);
        if (guardian.squaredDistanceTo(center) > 2.25
                || !world.isSpaceEmpty(player, player.getBoundingBox().offset(center.subtract(player.getPos())))) return false;
        arrived = true; guardian.routes.stop(false);
        player.setVelocity(Vec3d.ZERO); player.fallDistance = 0; player.setPosition(center);
        PlayerControl.syncPosition(player);
        guardian.getLookControl().lookAt(player, 30, 30);
        return true;
    }
    private boolean attendantReady() {
        if (worker != null && (!worker.isAlive() || worker.isRemoved() || worker.getWorld() != world || !owns(worker.getUuid()))) {
            if (owns(worker.getUuid())) { GuardianRitual.releaseWorker(worker); releaseParticipant(worker.getUuid()); }
            worker = null;
        }
        if (worker == null && ticks % 20 == 0) {
            worker = GuardianVillages.villagers(world, pedestal).stream().filter(v -> !v.isBaby() && !v.isAiDisabled()
                    && v.getMainHandStack().isEmpty() && !GuardianRitual.assigned(v))
                    .sorted(Comparator.comparingDouble(v -> v.squaredDistanceTo(player)))
                    .filter(v -> reserve(DrakeOutpostOwnership.get(world.getServer()).assignments(), List.of(v.getUuid())))
                    .findFirst().orElse(null);
            if (worker != null) {
                worker.wakeUp(); worker.addCommandTag("ssc_extras_guardian_worker");
                player.sendMessage(Text.translatable("message.ssc-extras.guardian.capture_fitting"), false);
            }
        }
        if (worker == null) return false;
        if (worker.squaredDistanceTo(player) > 6.25 || !worker.canSee(player)) {
            GuardianRitual.walk(worker, player.getPos(), .8); return false;
        }
        worker.getNavigation().stop(); worker.getLookControl().lookAt(player); return true;
    }
    public void struggle() {
        long now = world.getTime();
        if (control == null || !control.active() || lastStruggle == now || player.getWorld() != world) return;
        lastStruggle = now;
        if (player.getRandom().nextFloat() < .05f) {
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.capture_escaped"), true);
            finish();
        }
    }
    @Override protected void releaseParticipants() {
        if (guardian.capture == this) guardian.capture = null;
        // A powered return from mid-air needs the existing flight controller until its home route lands.
        guardian.grabbing(false); guardian.routes.stop(!guardian.recharging() || !guardian.powered());
        guardian.recaptureAfter = world.getTime() + 100;
        if (worker != null && owns(worker.getUuid())) GuardianRitual.releaseWorker(worker);
        if (world.getBlockEntity(pedestal) instanceof GuardianOffering offering) offering.cancelFlight(player.getUuid());
        if (control != null && control.active()) control.close();
        player.fallDistance = 0;
    }
    private final class Hold implements PlayerBrain {
        public net.minecraft.world.World world() { return world; }
        public Vec3d movement() { return Vec3d.ZERO; }
        public boolean freeLook() { return true; }
        public void stop() { finish(); }
        public void think() {
            if (!guardian.isAlive() || guardian.isRemoved() || guardian.getWorld() != world) { finish(); return; }
            // Native movement happens after the goal tick. Complete arrival before checking the carried body's next step.
            placeOnPedestal();
            double radians = Math.toRadians(guardian.bodyYaw);
            Vec3d destination = arrived ? GuardianPedestal.position(pedestal)
                    : guardian.getPos().add(-Math.sin(radians) * 1.05, .85, Math.cos(radians) * 1.05);
            if (!arrived) {
                Vec3d safe = null;
                for (double lift = 0; lift <= 1.5; lift += .25) {
                    var candidate = destination.add(0, lift, 0);
                    // Clear the step vertically before moving forward; never drop a captive just for meeting a stair/base.
                    double height = Math.max(player.getY(), candidate.y);
                    var raised = player.getBoundingBox().offset(0, height - player.getY(), 0);
                    if (world.isSpaceEmpty(player, player.getBoundingBox().stretch(0, height - player.getY(), 0))
                            && world.isSpaceEmpty(player, raised.stretch(candidate.x - player.getX(), 0, candidate.z - player.getZ()))
                            && world.isSpaceEmpty(player, player.getBoundingBox().offset(candidate.subtract(player.getPos())))) { safe = candidate; break; }
                }
                if (safe == null) {
                    guardian.routes.stop(false);
                    guardian.routes.moveTo(guardian.getPos().add(0, 1, 0), .8, true);
                    player.setVelocity(Vec3d.ZERO); player.fallDistance = 0; return;
                }
                destination = safe;
            } else if (!world.isSpaceEmpty(player, player.getBoundingBox().offset(destination.subtract(player.getPos())))) { finish(); return; }
            player.setVelocity(Vec3d.ZERO); player.fallDistance = 0;
            player.setPosition(destination);
        }
    }
}
