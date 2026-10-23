package sscextras.guardian;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctManager;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import java.util.*;

public final class GuardianVillages {
    public static final Identifier VISION = RuneboundGuardian.id("guardian_vision");
    public static final int RADIUS = 64;
    private record Threat(UUID villager, UUID attacker, long expires) { }
    private static final Map<ServerWorld, Map<UUID, Threat>> THREATS = new WeakHashMap<>();
    private GuardianVillages() { }
    public static GuardianWorldState.Village claim(PlayerEntity player) {
        return player.getWorld().isClient ? null : GuardianWorldState.get(player.getServer()).villages.get(player.getUuid());
    }
    public static List<VillagerEntity> villagers(ServerWorld world, BlockPos center) {
        return world.getEntitiesByClass(VillagerEntity.class, new Box(center).expand(RADIUS), Entity::isAlive);
    }
    public static boolean inVillage(ServerPlayerEntity player) {
        return player.getServerWorld().isNearOccupiedPointOfInterest(player.getBlockPos());
    }
    public static void bind(ServerPlayerEntity player, BlockPos pos) {
        var data = GuardianWorldState.get(player.getServer());
        var world = player.getServerWorld();
        if (RuneboundGuardian.stage(player) < 0 || !player.isAlive() || player.isSpectator()
                || GuardianCombat.locked(player) || !GuardianPedestal.intact(world, pos)
                || world.getBlockState(pos).get(GuardianPedestal.OFFERING)) return;
        if (world.getBlockEntity(pos) instanceof GuardianOffering offering && offering.hasResident()
                || sscextras.drake.DrakeOutpostOwnership.get(player.getServer()).assignments().owner(GuardianPedestal.reservation(world, pos)) != null) {
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.pedestal_taken"), true); return;
        }
        for (var entry : data.villages.entrySet()) if (!entry.getKey().equals(player.getUuid())
                && entry.getValue().world.equals(world.getRegistryKey()) && entry.getValue().pedestal.equals(pos)) {
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.pedestal_taken"), true); return;
        }
        var old = claim(player);
        if (old != null && old.world.equals(world.getRegistryKey()) && old.pedestal.equals(pos)) {
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.service", old.serviceTicks / 24000, 10), true); return;
        }
        var village = new GuardianWorldState.Village(); village.world = world.getRegistryKey(); village.pedestal = pos.toImmutable();
        GuardianDialogue.stop(player);
        if (old != null && old.ritualTicks > 0) GuardianRitual.cancel(player, old);
        data.villages.put(player.getUuid(), village); data.markDirty();
        player.sendMessage(Text.translatable("message.ssc-extras.guardian.pedestal_bound"), true);
    }
    public static void pedestalBroken(ServerWorld world, BlockPos pos) {
        var data = GuardianWorldState.get(world.getServer());
        data.villages.forEach((owner, village) -> {
            if (!village.world.equals(world.getRegistryKey()) || !village.pedestal.equals(pos)) return;
            village.resetService(); village.eligible = false;
            var player = world.getServer().getPlayerManager().getPlayer(owner);
            if (player != null) { GuardianDialogue.stop(player); GuardianRitual.cancel(player, village); }
        });
        data.markDirty();
    }
    public static void harmed(VillagerEntity villager, Entity attacker) {
        if (!(villager.getWorld() instanceof ServerWorld world) || !(attacker instanceof LivingEntity)) return;
        GuardianVillageMobs.harmed(villager, attacker);
        rememberThreat(world, villager, attacker);
        if (attacker instanceof MobEntity mob) {
            var target = redirectTarget(mob, villager);
            if (target != villager) {
                mob.setTarget(target);
                if (mob.getBrain().hasMemoryModuleWithValue(MemoryModuleType.ATTACK_TARGET, villager))
                    mob.getBrain().remember(MemoryModuleType.ATTACK_TARGET, target);
            }
        }
    }
    public static boolean activeGuardian(ServerPlayerEntity player) {
        return RuneboundGuardian.stage(player) == 3 && player.isAlive() && !player.isCreative()
                && !player.isSpectator() && !player.isSleeping() && !GuardianCombat.locked(player)
                && RuneboundGuardian.state(player).energy > 0;
    }
    public static LivingEntity redirectTarget(MobEntity attacker, LivingEntity target) {
        if (!(target instanceof VillagerEntity villager) || !villager.isAlive() || !attacker.isAlive()
                || !(attacker.getWorld() instanceof ServerWorld world)) return target;
        var guardian = world.getPlayers().stream().filter(player -> activeGuardian(player)
                && player.squaredDistanceTo(villager) <= RADIUS * RADIUS
                && player.squaredDistanceTo(attacker) <= RADIUS * RADIUS
                && !attacker.isTeammate(player) && attacker.canTarget(player)
                && player.canSee(villager) && attacker.canSee(player))
                .min(Comparator.comparingDouble((ServerPlayerEntity player) -> player.squaredDistanceTo(attacker))
                        .thenComparing(Entity::getUuid)).orElse(null);
        if (guardian == null) return target;
        rememberThreat(world, villager, attacker);
        return guardian;
    }
    private static void rememberThreat(ServerWorld world, VillagerEntity villager, Entity attacker) {
        THREATS.computeIfAbsent(world, key -> new HashMap<>()).put(villager.getUuid(),
                new Threat(villager.getUuid(), attacker.getUuid(), world.getTime() + 100));
    }
    private static Collection<Threat> threats(ServerWorld world) {
        return THREATS.computeIfAbsent(world, key -> new HashMap<>()).values();
    }
    public static LivingEntity offender(ServerPlayerEntity player) {
        return threats(player.getServerWorld()).stream().filter(t -> t.expires >= player.getWorld().getTime())
                .map(t -> player.getServerWorld().getEntity(t.attacker)).filter(e -> e instanceof LivingEntity && e.isAlive()
                        && e != player && !(e instanceof VillagerEntity) && !e.isTeammate(player)
                        && !(e instanceof PlayerEntity target && (target.isCreative() || target.isSpectator() || !player.getServer().isPvpEnabled()))
                        && e.squaredDistanceTo(player) <= 16 * 16)
                .map(e -> (LivingEntity)e).min(Comparator.comparingDouble(e -> e.squaredDistanceTo(player))).orElse(null);
    }
    public static boolean sprintingToHelp(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity server)) return RuneboundGuardian.state(player).protecting;
        for (var threat : threats(server.getServerWorld())) {
            var villager = server.getServerWorld().getEntity(threat.villager);
            if (villager == null || !villager.isAlive() || threat.expires < player.getWorld().getTime()) continue;
            Vec3d delta = villager.getPos().subtract(player.getPos());
            if (delta.lengthSquared() <= 64 * 64 && player.getRotationVec(1).dotProduct(delta.normalize()) > .5) return true;
        }
        return false;
    }
    public static void tick(ServerPlayerEntity player) {
        int stage = RuneboundGuardian.stage(player);
        var state = RuneboundGuardian.state(player);
        var claim = claim(player);
        if (claim != null) {
            if (claim.ritualTicks > 0) { GuardianRitual.tick(player, claim); return; }
            if (stage == 2 && player.age % 20 == 0) {
                updateService(player, claim);
                if (claim.readyDay >= 0 && claim.eligible && player.getWorld().isDay()
                        && Math.floorDiv(player.getWorld().getTimeOfDay(), 24000) >= claim.readyDay)
                    GuardianRitual.start(player, claim);
                GuardianWorldState.get(player.getServer()).markDirty();
            }
        }
        GuardianDialogue.tick(player);
        GuardianRepairEvent.update(player);
        if (!InstinctTicker.isPausing && stage < 3 && !player.isCreative() && !player.isSpectator() && player.age % 100 == 0) {
            if (inVillage(player)) InstinctManager.applyImmediateEffect(player, "SSC_EXTRAS_GUARDIAN_VILLAGE", .1f);
            if (GuardianPedestal.standing(player)) InstinctManager.applyImmediateEffect(player, "SSC_EXTRAS_GUARDIAN_PEDESTAL", .2f);
        }
        if (stage >= 2 && player.age % 20 == 0) sendVision(player);
        if(state.brain==null && state.protecting) stopProtecting(player);
        if (stage != 3 || state.energy <= 0 || player.isCreative() || player.isSpectator() || player.isSleeping() || GuardianCombat.locked(player)) { stopProtecting(player); return; }
        if (state.brain != null && (!state.brain.valid() || state.brain.getWorld() != player.getWorld())) stopProtecting(player);
        if (state.brain == null && player.age % 10 == 0) {
            var offender = offender(player);
            if (offender != null) {
                var brain = new GuardianProtectBrain(player, offender);
                if (brain.route()) {
                    state.brain = brain; state.protecting = true; state.sync();
                    player.sendMessage(Text.translatable("message.ssc-extras.guardian.protect"), false);
                } else brain.stop();
            }
        }
        if (state.brain != null) state.brain.think();
    }
    public static void stopProtecting(ServerPlayerEntity player) {
        var state = RuneboundGuardian.state(player);
        if (state.brain != null) { state.brain.stop(); state.brain = null; }
        if (state.protecting) { state.protecting = false; player.setSprinting(false); state.sync(); }
    }
    public static boolean eligible(ServerPlayerEntity player, GuardianWorldState.Village village) {
        return RuneboundGuardian.stage(player) == 2 && player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.getWorld().getRegistryKey().equals(village.world) && GuardianEquipment.complete(player)
                && player.squaredDistanceTo(GuardianPedestal.position(village.pedestal)) <= GuardianEquipment.RANGE * GuardianEquipment.RANGE
                && GuardianPedestal.intact(player.getWorld(), village.pedestal)
                && player.getServerWorld().isNearOccupiedPointOfInterest(village.pedestal)
                && villagers(player.getServerWorld(), village.pedestal).size() >= 3;
    }
    public static void updateService(ServerPlayerEntity player, GuardianWorldState.Village village) {
        long now = player.getWorld().getTimeOfDay();
        long elapsed = village.lastServiceTick == Long.MIN_VALUE || !village.eligible ? 20 : Math.max(0, now - village.lastServiceTick);
        village.lastServiceTick = now;
        village.eligible = eligible(player, village);
        if (!GuardianEquipment.complete(player)) village.resetService();
        if (!village.eligible) return;
        long previous = village.serviceTicks;
        long needed = GuardianWorldState.SERVICE_TICKS - previous;
        village.serviceTicks += Math.min(needed, elapsed);
        int hint = village.serviceTicks >= 8 * 24000L ? 8 : village.serviceTicks >= 5 * 24000L ? 5 : 0;
        if (hint > village.serviceHint) {
            // Date the milestone at its actual crossing, so time skips do not replay old dialogue.
            long reachedAt = now - Math.max(0, elapsed - (hint * 24000L - previous));
            village.serviceHint = hint;
            village.serviceHintDay = Math.floorDiv(reachedAt, 24000);
        }
        if (village.serviceTicks == GuardianWorldState.SERVICE_TICKS && village.readyDay < 0) {
            long completedAt = now - Math.max(0, elapsed - needed);
            village.readyDay = Math.floorDiv(completedAt, 24000) + 1;
        }
    }
    public static void stopRepair(ServerPlayerEntity player) {
        var event = RuneboundGuardian.state(player).repair;
        if (event != null) event.finish();
    }
    private static void sendVision(ServerPlayerEntity player) {
        var entries = threats(player.getServerWorld()).stream().filter(t -> t.expires >= player.getWorld().getTime()).toList();
        if (!ServerPlayNetworking.canSend(player, VISION)) return;
        var buffer = PacketByteBufs.create(); buffer.writeVarInt(entries.size());
        for (var threat : entries) { buffer.writeUuid(threat.villager); buffer.writeUuid(threat.attacker); buffer.writeVarInt((int)(threat.expires - player.getWorld().getTime())); }
        ServerPlayNetworking.send(player, VISION, buffer);
    }
    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity,world)->{
            if(entity instanceof VillagerEntity villager && villager.getCommandTags().contains("ssc_extras_guardian_worker"))
                GuardianRitual.releaseWorker(villager);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            var event = sscextras.drake.DrakeOutpostOwnership.get(world.getServer()).assignments().owner(entity.getUuid());
            if (event instanceof GuardianCoreRecoveryEvent recovery) recovery.suspend();
            else if (event instanceof GuardianRepairEvent repair) repair.finish();
        });
        ServerTickEvents.START_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 != 0 || world.getPlayers().stream().noneMatch(p -> RuneboundGuardian.stage(p) >= 2)) return;
            threats(world).removeIf(threat -> threat.expires < world.getTime());
            Set<UUID> seen = new HashSet<>();
            for (var player : world.getPlayers()) if (RuneboundGuardian.stage(player) >= 2)
                for (var mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(64), Entity::isAlive))
                    if (seen.add(mob.getUuid())) {
                        var target = mob.getTarget();
                        if (target == null && mob.getBrain().hasMemoryModule(MemoryModuleType.ATTACK_TARGET))
                            target = mob.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                        if (target instanceof VillagerEntity villager) harmed(villager, mob);
                    }
            for (var player : world.getPlayers()) if (RuneboundGuardian.stage(player) == 3)
                for (var villager : villagers(world, player.getBlockPos()))
                    GuardianVillagerProtection.get(villager).sample(villager, player);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayerEntity player) {
                var village = claim(player);
                if (village != null && village.ritualTicks > 0) GuardianRitual.cancel(player, village);
                GuardianRecovery.stop(player); stopRepair(player);
                return;
            }
            if (!(entity instanceof VillagerEntity) || !(entity.getWorld() instanceof ServerWorld world)) return;
            var data = GuardianWorldState.get(world.getServer());
            data.villages.forEach((owner, village) -> {
                if (!village.world.equals(world.getRegistryKey()) || !new Box(village.pedestal).expand(RADIUS).contains(entity.getPos())) return;
                village.resetService();
                var player = world.getServer().getPlayerManager().getPlayer(owner);
                if (player != null && RuneboundGuardian.stage(player) == 2) {
                    GuardianDialogue.stop(player);
                    GuardianRitual.cancel(player, village);
                    player.sendMessage(Text.translatable("message.ssc-extras.guardian.service_reset"), false);
                }
            });
            data.markDirty();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            GuardianDialogue.stop(handler.player);
            stopProtecting(handler.player); GuardianRitual.stopEscort(handler.player);
            var village=claim(handler.player);
            var state = RuneboundGuardian.state(handler.player);
            stopRepair(handler.player);
            if (village != null) { village.lastServiceTick = Long.MIN_VALUE; village.eligible = false; }
            if(village!=null && (village.ritualTicks>0||village.rebuildTicks>0)) GuardianRitual.suspend(handler.player,village);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerManager().getPlayerList()) if (RuneboundGuardian.state(player).protecting || RuneboundGuardian.state(player).deactivated || RuneboundGuardian.state(player).escort != null)
                player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        });
    }
}
