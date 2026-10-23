package sscextras.guardian;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.util.EnumSet;

/** One native navigation owner for village patrol, pursuit and pedestal return. */
final class GuardianVillageGoal extends Goal {
    private final GuardianShellEntity guardian;
    private int attackCooldown, routeCooldown, captureCooldown;
    GuardianVillageGoal(GuardianShellEntity guardian) {
        this.guardian = guardian; setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP));
    }
    @Override public boolean canStart() { return guardian.resident() && guardian.powered() && !guardian.core.isEmpty() && !guardian.placedStatue() && guardian.work() == 0 && GuardianCores.id(guardian.core) == null; }
    @Override public boolean shouldRunEveryTick() { return true; }
    @Override public void stop() {
        guardian.routes.stop(true); guardian.awake(false);
        if (guardian.capture != null) guardian.capture.finish();
    }
    @Override public void tick() {
        var world = (ServerWorld)guardian.getWorld();
        if (attackCooldown > 0) attackCooldown--;
        if (routeCooldown > 0) routeCooldown--;
        if (captureCooldown > 0) captureCooldown--;
        var player = guardian.offender == null ? null : world.getServer().getPlayerManager().getPlayer(guardian.offender);
        if (player != null && (guardian.recharging() || !player.isAlive() || player.isCreative() || player.isSpectator() || player.getWorld() != world
                || world.getTime() > guardian.alertUntil || player.squaredDistanceTo(Vec3d.ofCenter(guardian.home())) > 128 * 128)) player = null;
        if (guardian.capture != null) { guardian.awake(true); guardian.capture.tick(); return; }
        if (player != null) {
            guardian.awake(true);
            if (world.getTime() >= guardian.recaptureAfter && captureCooldown == 0) {
                guardian.capture = GuardianCaptureEvent.start(guardian, player);
                captureCooldown = 20;
                if (guardian.capture != null) { guardian.capture.tick(); return; }
            }
            guardian.getLookControl().lookAt(player, 30, 30);
            if (routeCooldown == 0) { guardian.routes.moveTo(player.getPos(), 1.15, false); routeCooldown = 10; }
            if (world.getTime() >= guardian.recaptureAfter && guardian.squaredDistanceTo(player) < 6.25
                    && guardian.canSee(player) && attackCooldown == 0) {
                guardian.tryAttack(player); guardian.swingHand(net.minecraft.util.Hand.MAIN_HAND); attackCooldown = 20;
            }
            return;
        }
        guardian.offender = null;
        boolean returnHome = guardian.sentry() || world.isDay() || guardian.recharging() || guardian.squaredDistanceTo(Vec3d.ofCenter(guardian.home())) > 40 * 40;
        if (returnHome) {
            var destination = GuardianPedestal.position(guardian.home());
            if (GuardianPedestal.intact(world, guardian.home()) && guardian.squaredDistanceTo(destination) < 6.25
                    && world.isSpaceEmpty(guardian, guardian.getBoundingBox().offset(destination.subtract(guardian.getPos())))) {
                if (guardian.squaredDistanceTo(destination) < .16) {
                    guardian.setPosition(destination); guardian.routes.stop(true); guardian.awake(false);
                } else { guardian.awake(true); guardian.routes.moveTo(destination, .8, false); }
            } else {
                guardian.awake(true);
                if (routeCooldown == 0) {
                    guardian.routes.moveTo(destination, .8, false); routeCooldown = 40;
                }
            }
        } else {
            guardian.awake(true);
            if (guardian.getNavigation().isIdle() && routeCooldown == 0) {
                var destination = NoPenaltyTargeting.find(guardian, 12, 5);
                if (destination != null && destination.squaredDistanceTo(Vec3d.ofCenter(guardian.home())) <= 40 * 40)
                    guardian.routes.moveTo(destination, .65, false);
                routeCooldown = 60 + guardian.getRandom().nextInt(100);
            }
        }
    }
}
