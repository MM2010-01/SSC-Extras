package sscextras.guardian;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import sscextras.drake.DrakeOutpostOwnership;
import sscextras.events.AssignedNpcEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static sscextras.guardian.GuardianRitual.*;

/** One detached core's retrieval, delivery and insertion, driven by its physical location. */
final class GuardianCoreRecoveryEvent extends AssignedNpcEvent {
    private final ServerPlayerEntity player;
    private final GuardianWorldState.Core core;
    private GuardianShellEntity body;
    private VillagerEntity worker;

    GuardianCoreRecoveryEvent(ServerPlayerEntity player, GuardianWorldState.Core core) {
        this.player = player; this.core = core;
    }
    boolean available(Entity entity) {
        var owner = DrakeOutpostOwnership.get(player.getServer()).assignments().owner(entity.getUuid());
        return owner == null || owner == this;
    }
    boolean body(GuardianShellEntity next) {
        if (!reserve(DrakeOutpostOwnership.get(player.getServer()).assignments(),
                List.of(player.getUuid(), core.id, next.getUuid()))) return false;
        if (body != null && body != next) releaseParticipant(body.getUuid());
        if (!Objects.equals(core.recoveryBody, next.getUuid())) core.insertionTicks = 1;
        body = next; core.recoveryBody = next.getUuid();
        return true;
    }
    boolean worker(VillagerEntity next) {
        if (body == null || !reserve(DrakeOutpostOwnership.get(player.getServer()).assignments(),
                List.of(player.getUuid(), core.id, body.getUuid(), next.getUuid()))) return false;
        if (worker != null && worker != next) releaseWorker();
        if (!Objects.equals(core.rescuer, next.getUuid())) core.insertionTicks = 1;
        worker = next; core.rescuer = next.getUuid();
        worker.addCommandTag("ssc_extras_guardian_worker");
        return true;
    }
    private void releaseWorker() {
        if (worker == null) return;
        if (owns(worker.getUuid())) GuardianRitual.releaseWorker(worker);
        releaseParticipant(worker.getUuid()); worker = null;
    }
    void resetWork() {
        releaseWorker();
        if (body != null) { releaseParticipant(body.getUuid()); body = null; }
        core.rescuer = core.recoveryBody = null;
        core.recoveryTicks = core.insertionTicks = 0;
        var village = GuardianVillages.claim(player);
        if (village != null && village.ritualTicks == 0) village.rebuildTicks = village.recoveryTicks = 0;
    }
    void suspend() { GuardianRecovery.suspend(player); }

    void tick(GuardianWorldState.Village village) {
        if (!reserve(DrakeOutpostOwnership.get(player.getServer()).assignments(), List.of(player.getUuid(), core.id))) {
            pause(); return;
        }
        resume();
        if (GuardianRecovery.returnByTrader(player, village, this)) return;
        var world = player.getServer().getWorld(core.world);
        if (world == null) { resetWork(); pause(); return; }
        Entity holder = core.holder == null ? null : world.getEntity(core.holder);
        var held = holder instanceof ItemEntity item ? item.getStack()
                : holder instanceof VillagerEntity bearer && bearer.isAlive() ? bearer.getMainHandStack() : ItemStack.EMPTY;
        if (!core.id.equals(GuardianCores.id(held)) || !available(holder)) { resetWork(); pause(); return; }
        if (village != null && core.world.equals(village.world) && village.bodyPos != null
                && village.bodyPos.isWithinDistance(holder.getPos(), GuardianEquipment.RANGE)) world.getChunk(village.bodyPos);
        Entity source = holder;
        var target = world.getEntitiesByClass(GuardianShellEntity.class, holder.getBoundingBox().expand(GuardianEquipment.RANGE),
                        shell -> shell.isAlive() && shell.stage() == 3 && shell.work() == 0 && shell.core.isEmpty()
                                && shell.squaredDistanceTo(source) <= GuardianEquipment.RANGE * GuardianEquipment.RANGE && available(shell))
                .stream().min(Comparator.comparingDouble(shell -> shell.squaredDistanceTo(source))).orElse(null);
        if (target == null) {
            if (village == null || !core.world.equals(village.world)
                    || holder.squaredDistanceTo(GuardianPedestal.position(village.pedestal)) > GuardianEquipment.RANGE * GuardianEquipment.RANGE) {
                resetWork(); pause(); return;
            }
            world.getChunk(village.pedestal);
            if (!GuardianPedestal.intact(world, village.pedestal) || !world.isNearOccupiedPointOfInterest(village.pedestal)) {
                resetWork(); pause(); return;
            }
            target = GuardianRecovery.waitingBody(player, village, world, GuardianPedestal.position(village.pedestal), this);
            if (target == null) { resetWork(); pause(); return; }
        }
        if (!body(target)) { resetWork(); pause(); return; }
        Vec3d center = target.getPos();
        core.recoveryTicks++;
        if (village != null) { village.recoveryTicks = core.recoveryTicks; village.rebuildTicks = Math.max(1, core.insertionTicks); }
        if (holder instanceof ItemEntity item) {
            var rescuer = world.getEntitiesByClass(VillagerEntity.class, item.getBoundingBox().expand(GuardianEquipment.RANGE),
                            v -> v.isAlive() && !v.isBaby() && !v.isAiDisabled() && v.getMainHandStack().isEmpty()
                                    && available(v) && (!assigned(v) || owns(v.getUuid())))
                    .stream().min(Comparator.comparingInt((VillagerEntity v) -> v.getUuid().equals(core.rescuer) ? 0 : 1)
                            .thenComparingDouble(v -> v.squaredDistanceTo(item))).orElse(null);
            boolean fallback = core.recoveryTicks >= 300 && GuardianRecovery.tradersSpawn(world)
                    && (village == null || world.isNearOccupiedPointOfInterest(village.pedestal));
            boolean created = rescuer == null && fallback;
            if (created) {
                rescuer = EntityType.VILLAGER.create(world);
                if (rescuer == null) return;
                Vec3d at = safeStation(rescuer, center, 0);
                if (at == null) return;
                rescuer.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
                rescuer.initialize(world, world.getLocalDifficulty(target.getBlockPos()), SpawnReason.EVENT, null, null);
                rescuer.setBreedingAge(0); rescuer.setPersistent();
                if (!world.spawnEntity(rescuer)) return;
            }
            if (rescuer == null) { pause(); return; }
            if (!worker(rescuer)) { if (created) rescuer.discard(); pause(); return; }
            if (!fallback && !walk(rescuer, item.getPos(), 1)) { pause(); return; }
            if (fallback && !recoveryCut(rescuer, center)) return;
            rescuer.equipStack(EquipmentSlot.MAINHAND, item.getStack());
            rescuer.setEquipmentDropChance(EquipmentSlot.MAINHAND, 2);
            item.setStack(ItemStack.EMPTY); item.discard();
            GuardianCores.observe(rescuer.getMainHandStack(), rescuer, "entity");
            holder = rescuer; core.insertionTicks = 1;
        }
        var bearer = (VillagerEntity)holder;
        if (!worker(bearer)) { pause(); return; }
        GuardianCores.observe(bearer.getMainHandStack(), bearer, "entity");
        Vec3d at = safeStation(bearer, center, 0);
        if (at == null) { pause(); return; }
        if (!walk(bearer, at, 1)) {
            core.insertionTicks = 1; pause();
            if (core.recoveryTicks >= 300 && GuardianRecovery.tradersSpawn(world)) recoveryCut(bearer, center);
            return;
        }
        bearer.getLookControl().lookAt(center.x, center.y + 1.4, center.z);
        core.insertionTicks++;
        if (core.insertionTicks == 20) {
            bearer.swingHand(Hand.MAIN_HAND);
            sound(world, center, SoundEvents.BLOCK_BEACON_ACTIVATE, 1);
        }
        if (core.insertionTicks >= 40 && GuardianCores.occupy(player, target, bearer.getMainHandStack())) {
            bearer.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.rebuilt"), false);
        }
    }
    private boolean recoveryCut(VillagerEntity bearer, Vec3d center) {
        Vec3d at = safeStation(bearer, center, 0); if (at == null) return false;
        var state = RuneboundGuardian.state(player);
        state.recoveryBlackout = 25; state.recoveryHintPending = true; state.sync();
        bearer.getNavigation().stop(); bearer.setVelocity(Vec3d.ZERO);
        bearer.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        return true;
    }
    @Override protected void releaseParticipants() {
        if (worker != null && owns(worker.getUuid())) GuardianRitual.releaseWorker(worker);
    }
}
