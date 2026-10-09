package sscextras.guardian;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
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
        if (!GuardianPedestal.intact(world, pos) || !world.isNearOccupiedPointOfInterest(pos) || villagers(world, pos).size() < 3) {
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.village_required"), true); return;
        }
        for (var entry : data.villages.entrySet()) if (!entry.getKey().equals(player.getUuid())
                && entry.getValue().world.equals(world.getRegistryKey()) && entry.getValue().pedestal.equals(pos)) {
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.pedestal_taken"), true); return;
        }
        var old = claim(player);
        if (old != null && old.world.equals(world.getRegistryKey()) && old.pedestal.equals(pos)) {
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.service", old.serviceTicks / 24000, 10), true); return;
        }
        if (old != null && GuardianEquipment.confined(player)) {
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.cuffs_boundary", GuardianEquipment.RANGE), true); return;
        }
        var village = new GuardianWorldState.Village(); village.world = world.getRegistryKey(); village.pedestal = pos.toImmutable();
        data.villages.put(player.getUuid(), village); data.markDirty();
        player.sendMessage(Text.translatable("message.ssc-extras.guardian.pedestal_bound"), true);
    }
    public static void pedestalBroken(ServerWorld world, BlockPos pos) {
        var data = GuardianWorldState.get(world.getServer());
        data.villages.forEach((owner, village) -> {
            if (!village.world.equals(world.getRegistryKey()) || !village.pedestal.equals(pos)) return;
            village.serviceTicks = 0; village.readyDay = -1; village.eligible = false;
            var player = world.getServer().getPlayerManager().getPlayer(owner);
            if (player != null) GuardianRitual.cancel(player, village);
        });
        data.markDirty();
    }
    public static void harmed(VillagerEntity villager, Entity attacker) {
        if (!(villager.getWorld() instanceof ServerWorld world) || !(attacker instanceof LivingEntity)) return;
        THREATS.computeIfAbsent(world, key -> new HashMap<>()).put(villager.getUuid(), new Threat(villager.getUuid(), attacker.getUuid(), world.getTime() + 100));
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
                claim.eligible = eligible(player, claim);
                if (claim.eligible) addService(player, claim, 20);
                else if (!GuardianEquipment.complete(player)) { claim.serviceTicks = 0; claim.readyDay = -1; }
                if (claim.readyDay >= 0 && claim.eligible && player.getWorld().isDay()
                        && Math.floorDiv(player.getWorld().getTimeOfDay(), 24000) >= claim.readyDay)
                    GuardianRitual.start(player, claim);
                GuardianWorldState.get(player.getServer()).markDirty();
            }
        }
        if (!InstinctTicker.isPausing && stage < 3 && !player.isCreative() && !player.isSpectator() && player.age % 100 == 0) {
            if (inVillage(player)) InstinctManager.applyImmediateEffect(player, "SSC_EXTRAS_GUARDIAN_VILLAGE", .1f);
            if (GuardianPedestal.standing(player)) InstinctManager.applyImmediateEffect(player, "SSC_EXTRAS_GUARDIAN_PEDESTAL", .2f);
        }
        if (stage >= 2 && player.age % 20 == 0) sendVision(player);
        if(state.brain==null && state.protecting) stopProtecting(player);
        if (stage != 3 || state.energy <= 0 || player.isCreative() || player.isSpectator() || player.isSleeping() || state.detached) { stopProtecting(player); return; }
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
                && villagers(player.getServerWorld(), village.pedestal).size() >= 3;
    }
    private static void addService(ServerPlayerEntity player, GuardianWorldState.Village village, long ticks) {
        village.serviceTicks = Math.min(GuardianWorldState.SERVICE_TICKS, village.serviceTicks + ticks);
        if (village.serviceTicks == GuardianWorldState.SERVICE_TICKS && village.readyDay < 0)
            village.readyDay = Math.floorDiv(player.getWorld().getTimeOfDay(), 24000) + 1;
    }
    public static void skipped(ServerPlayerEntity player, long ticks) {
        var village = claim(player);
        if (village != null && ticks <= 24000 && player.isSleeping() && eligible(player, village)) {
            addService(player, village, ticks); GuardianWorldState.get(player.getServer()).markDirty();
        }
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
        ServerTickEvents.START_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 != 0 || world.getPlayers().stream().noneMatch(p -> RuneboundGuardian.stage(p) >= 2)) return;
            threats(world).removeIf(threat -> threat.expires < world.getTime());
            Set<UUID> seen = new HashSet<>();
            for (var player : world.getPlayers()) if (RuneboundGuardian.stage(player) >= 2)
                for (var mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(64), Entity::isAlive))
                    if (seen.add(mob.getUuid()) && mob.getTarget() instanceof VillagerEntity villager) harmed(villager, mob);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof VillagerEntity) || !(entity.getWorld() instanceof ServerWorld world)) return;
            var data = GuardianWorldState.get(world.getServer());
            data.villages.forEach((owner, village) -> {
                if (!village.world.equals(world.getRegistryKey()) || !new Box(village.pedestal).expand(RADIUS).contains(entity.getPos())) return;
                village.serviceTicks = 0; village.readyDay = -1;
                var player = world.getServer().getPlayerManager().getPlayer(owner);
                if (player != null && RuneboundGuardian.stage(player) == 2) {
                    GuardianRitual.cancel(player, village);
                    player.sendMessage(Text.translatable("message.ssc-extras.guardian.service_reset"), false);
                }
            });
            data.markDirty();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            stopProtecting(handler.player); GuardianRitual.stopEscort(handler.player);
            var village=claim(handler.player);
            if(village!=null && (village.ritualTicks>0||village.rebuildTicks>0)) GuardianRitual.suspend(handler.player,village);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var player : server.getPlayerManager().getPlayerList()) if (RuneboundGuardian.state(player).protecting || RuneboundGuardian.state(player).escort != null)
                player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        });
    }
}
