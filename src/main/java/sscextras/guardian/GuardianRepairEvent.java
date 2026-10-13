package sscextras.guardian;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import sscextras.drake.DrakeOutpostOwnership;
import sscextras.events.AssignedNpcEvent;
import java.util.Comparator;
import java.util.List;

final class GuardianRepairEvent extends AssignedNpcEvent {
    private final ServerPlayerEntity player;
    private final VillagerEntity worker;
    private int ticks;

    private GuardianRepairEvent(ServerPlayerEntity player, VillagerEntity worker) {
        this.player = player; this.worker = worker;
    }
    private static boolean needed(ServerPlayerEntity player) {
        var state = RuneboundGuardian.state(player);
        return RuneboundGuardian.stage(player) >= 1 && player.getServerWorld().isDay() && player.isAlive()
                && !state.detached && state.ritualTicks == 0 && player.getHealth() < player.getMaxHealth();
    }
    static void update(ServerPlayerEntity player) {
        var state = RuneboundGuardian.state(player);
        if (state.repair == null && needed(player) && player.age % 40 == 0) {
            var worker = player.getServerWorld().getEntitiesByClass(VillagerEntity.class, player.getBoundingBox().expand(16),
                            v -> v.isAlive() && !v.isBaby() && !v.isAiDisabled() && !v.isSleeping()
                                    && v.getMainHandStack().isEmpty() && !GuardianRitual.assigned(v))
                    .stream().min(Comparator.comparingDouble(v -> v.squaredDistanceTo(player))).orElse(null);
            if (worker != null) {
                var event = new GuardianRepairEvent(player, worker);
                if (event.reserve(DrakeOutpostOwnership.get(player.getServer()).assignments(), List.of(player.getUuid(), worker.getUuid()))) {
                    state.repair = event;
                    worker.addCommandTag("ssc_extras_guardian_repair"); worker.addCommandTag("ssc_extras_guardian_worker");
                    GuardianRitual.prop(worker, Items.COBBLESTONE);
                }
            }
        }
        if (state.repair != null) state.repair.tick();
    }
    private void tick() {
        if (!needed(player) || !worker.isAlive() || worker.isRemoved() || worker.getWorld() != player.getWorld()
                || worker.squaredDistanceTo(player) > 32 * 32 || !owns(player.getUuid()) || !owns(worker.getUuid())) {
            finish(); return;
        }
        if (worker.squaredDistanceTo(player) > 6.25) {
            GuardianRitual.walk(worker, player.getPos(), .8); ticks = 0; pause(); return;
        }
        resume(); worker.getNavigation().stop(); worker.getLookControl().lookAt(player);
        if (++ticks % 40 == 0) {
            worker.swingHand(Hand.MAIN_HAND); player.heal(2);
            player.getServerWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.NEUTRAL, .7f, 1);
            player.getServerWorld().spawnParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1, player.getZ(), 4, .5, .5, .5, 0);
        }
    }
    @Override protected void releaseParticipants() {
        if (owns(worker.getUuid())) GuardianRitual.releaseWorker(worker);
        var state = RuneboundGuardian.state(player);
        if (state.repair == this) state.repair = null;
    }
}
