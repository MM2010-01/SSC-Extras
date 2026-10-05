package sscextras.drake;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import java.util.Comparator;

public final class DrakeSoulbinding {
    public static final int DAY_TICKS = 24000;
    public static final int SERVICE_TICKS = 10 * DAY_TICKS;
    public static final int RITUAL_TICKS = 600;

    private DrakeSoulbinding() { }

    public static void register() {
        SoulboundEquipment.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var claim = DrakeOutpostOwnership.claim(handler.player);
            if (claim != null) claim.lastServiceTime = handler.player.getWorld().getTimeOfDay();
        });
    }

    public static boolean bound(PlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        return claim != null && claim.soulbound;
    }

    public static boolean ritualActive(PlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        return claim != null && !claim.attendants.isEmpty();
    }

    public static void disobey(PlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null || claim.soulbound) return;
        claim.lastServiceTime = player.getWorld().getTimeOfDay();
        if (claim.goodTicks == 0) return;
        claim.goodTicks = 0; claim.ritualHint = 0;
        cancel(player, claim);
        hint(player, "service_reset");
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
    }

    static void tick(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        long time = player.getWorld().getTimeOfDay();
        long elapsed = claim.lastServiceTime == Long.MIN_VALUE ? 20 : Math.max(20, time - claim.lastServiceTime);
        claim.lastServiceTime = time;
        if (!player.isAlive() || player.isSpectator() || claim.awaitingRespawn) return;
        if (claim.soulbound) { SoulboundEquipment.enchant(player); return; }
        if (player.isCreative() || EarthenDrake.stage(player) < 0) { cancel(player, claim); return; }
        boolean serving = claim.world.equals(player.getWorld().getRegistryKey()) &&
                (DrakeCaptureGoal.near(claim.stable, player.getPos(), DrakeRoaming.RANGE) || DrakeBattleGoal.riding(player));
        if (!serving || claim.tryingToEscape) { disobey(player); return; }
        if (claim.goodTicks < SERVICE_TICKS) {
            claim.goodTicks += (int)Math.min(SERVICE_TICKS - claim.goodTicks, elapsed);
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        int hint = claim.goodTicks >= SERVICE_TICKS ? 3 : claim.goodTicks >= 8 * DAY_TICKS ? 2 : 1;
        if (hint > claim.ritualHint) {
            claim.ritualHint = hint;
            hint(player, hint == 1 ? "service_promise" : hint == 2 ? "ritual_soon" : "ritual_ready");
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
        }
        if (claim.goodTicks < SERVICE_TICKS) return;
        if (!atRitual(player, claim)) { cancel(player, claim); return; }
        var world = player.getServerWorld();
        if (claim.attendants.isEmpty()) {
            var guards = world.getEntitiesByClass(PillagerEntity.class, Box.from(claim.stable).expand(32), guard -> {
                if (!available(guard)) return false;
                var stable = ((DrakeStableNavigation)guard.getNavigation()).stable();
                return stable != null && claim.matches(world, stable) && attendee(guard) == null;
            });
            guards.sort(Comparator.comparingDouble(player::squaredDistanceTo));
            if (guards.size() < 3) return;
            for (int i = 0; i < 3; i++) claim.attendants.add(guards.get(i).getUuid());
            hint(player, "ritual_gathering");
        }
        for (int i = 0; i < 3; i++) {
            var entity = world.getEntity(claim.attendants.get(i));
            if (!(entity instanceof PillagerEntity guard) || !available(guard)) { cancel(player, claim); return; }
            if (guard.squaredDistanceTo(attendancePosition(claim, i)) > 2.25 || !guard.getVisibilityCache().canSee(player)) {
                if (claim.ritualTicks > 0) hint(player, "ritual_interrupted");
                claim.ritualTicks = 0; return;
            }
        }
        if (claim.ritualTicks == 0) hint(player, "ritual_begin");
        claim.ritualTicks += 20;
        world.spawnParticles(ParticleTypes.SOUL, player.getX(), player.getBodyY(.5), player.getZ(), 14, .7, .5, .7, .025);
        if (claim.ritualTicks == RITUAL_TICKS / 2) hint(player, "ritual_soul");
        if (claim.ritualTicks >= RITUAL_TICKS) complete(player, claim);
    }

    private static boolean atRitual(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        return claim.world.equals(player.getWorld().getRegistryKey()) && claim.stall().contains(player.getPos())
                && !player.isSleeping() && !player.hasPassengers() && !player.hasVehicle() && !DrakeBattleGoal.assigned(player)
                && !TransformManager.getPlayerTransformData(player).isTransforming
                && player.getWorld().getBlockState(player.getBlockPos().down()).isOf(Blocks.HAY_BLOCK);
    }

    static boolean available(PillagerEntity guard) {
        return !(guard instanceof DrakeVisitorEntity) && guard.isAlive() && !guard.isRemoved() && !guard.isAiDisabled() && !guard.hasActiveRaid()
                && !guard.hasVehicle() && !guard.hasPassengers() && !DrakeFaction.fighting(guard)
                && (DrakeBattleGoal.of(guard) == null || DrakeBattleGoal.of(guard).mount() == null)
                && ((DrakeCaptureGoal.Captor)guard).sscExtras$captureGoal().quarry() == null
                && ((DrakeFaction.EquipmentDisplay)guard).sscExtras$recruiting() == null;
    }

    static ServerPlayerEntity attendee(PillagerEntity guard) {
        if (!(guard.getWorld() instanceof ServerWorld world)) return null;
        for (var player : world.getPlayers()) {
            var claim = DrakeOutpostOwnership.claim(player);
            if (claim != null && claim.attendants.contains(guard.getUuid())) return player;
        }
        return null;
    }

    static Vec3d attendancePosition(DrakeOutpostOwnership.Claim claim, int index) {
        var bed = claim.bed();
        return switch (index) {
            case 0 -> Vec3d.ofBottomCenter(bed).add(-1.8, 0, -2);
            case 1 -> Vec3d.ofBottomCenter(bed).add(2.2, 0, -2);
            default -> Vec3d.ofBottomCenter(bed).add(.2, 0, 2.5);
        };
    }

    private static void cancel(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        if (claim.ritualTicks > 0) hint(player, "ritual_interrupted");
        claim.attendants.clear(); claim.ritualTicks = 0;
    }

    private static void complete(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        claim.previousSpawn.putString("World", player.getSpawnPointDimension().getValue().toString());
        if (player.getSpawnPointPosition() != null) claim.previousSpawn.putLong("Pos", player.getSpawnPointPosition().asLong());
        claim.previousSpawn.putFloat("Angle", player.getSpawnAngle());
        claim.previousSpawn.putBoolean("Forced", player.isSpawnForced());
        claim.soulbound = true;
        FormAbilityManager.applyForm(player, EarthenDrake.FORMS[3]);
        SoulboundEquipment.enchant(player);
        player.setSpawnPoint(claim.world, claim.bed(), 180, true, false);
        claim.attendants.clear(); claim.ritualTicks = 0;
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1, .7f);
        hint(player, "ritual_complete");
    }

    public static void formChanged(PlayerEntity player, PlayerFormBase form) {
        if (player.getWorld().isClient || !player.isAlive()) return;
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim != null && claim.soulbound && !claim.awaitingRespawn && EarthenDrake.stage(player) >= 0 && form.getGroup() != EarthenDrake.GROUP) {
            clearCurse(player, claim);
            DrakeOutpostOwnership.get(player.getServer()).markDirty();
            hint(player, "soul_freed");
        }
    }

    static void clearCurse(PlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        cancel(player, claim);
        if (!claim.soulbound) return;
        claim.soulbound = false;
        claim.awaitingRespawn = false;
        var spawn = claim.previousSpawn;
        claim.previousSpawn = new net.minecraft.nbt.NbtCompound();
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        RegistryKey<World> dimension = spawn.contains("World")
                ? RegistryKey.of(RegistryKeys.WORLD, new Identifier(spawn.getString("World"))) : World.OVERWORLD;
        serverPlayer.setSpawnPoint(dimension, spawn.contains("Pos") ? BlockPos.fromLong(spawn.getLong("Pos")) : null,
                spawn.getFloat("Angle"), spawn.getBoolean("Forced"), false);
    }

    public static void beforeRespawn(ServerPlayerEntity player, boolean alive) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null || !claim.soulbound) return;
        claim.awaitingRespawn = true;
        if (!alive) player.setSpawnPoint(claim.world, claim.bed(), 180, true, false);
    }

    public static void afterRespawn(ServerPlayerEntity player, boolean alive) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim == null || !claim.soulbound) return;
        FormAbilityManager.applyForm(player, EarthenDrake.FORMS[3]);
        claim.awaitingRespawn = false;
        SoulboundEquipment.enchant(player);
        if (!alive) {
            var world = player.getServer().getWorld(claim.world);
            if (world != null) {
                world.getChunk(claim.bed());
                var pos = respawnPosition(player, world, claim);
                if (pos != null) player.teleport(world, pos.x, pos.y, pos.z, 180, 0);
            }
            hint(player, "soul_respawn");
        }
        player.setSpawnPoint(claim.world, claim.bed(), 180, true, false);
        DrakeOutpostOwnership.tick(player);
        DrakeOutpostOwnership.get(player.getServer()).markDirty();
    }

    private static Vec3d respawnPosition(ServerPlayerEntity player, ServerWorld world, DrakeOutpostOwnership.Claim claim) {
        var center = claim.bed();
        for (int dy = 0; dy <= 3; dy++) for (int dz : new int[]{0, -1, 1, -2, 2}) for (int dx : new int[]{0, 1, -1}) {
            var block = center.add(dx, dy, dz);
            var pos = Vec3d.ofBottomCenter(block);
            if (world.getBlockState(block.down()).isSolidBlock(world, block.down())
                    && world.isSpaceEmpty(player, player.getDimensions(player.getPose()).getBoxAt(pos))) return pos;
        }
        return null;
    }

    private static void hint(PlayerEntity player, String key) {
        player.sendMessage(Text.translatable("message.ssc-extras.drake." + key).formatted(Formatting.LIGHT_PURPLE), false);
    }
}
