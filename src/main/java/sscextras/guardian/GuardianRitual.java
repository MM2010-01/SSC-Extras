package sscextras.guardian;

import net.minecraft.block.DoorBlock;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.*;

public final class GuardianRitual {
    public static final Item CHISEL = new Item(new Item.Settings().maxCount(1));
    public static final Item LIQUID_STONE = new Item(new Item.Settings().maxCount(1));
    public static final int SOUL_START = 120, SOUL_SEALED = 200, ENCASED = 360, SCULPTED = 560, COMPLETE = 660;
    private static final Map<VillagerEntity, Route> ROUTES = new WeakHashMap<>();
    private static final class Route {
        Vec3d position;
        int stuck;
        Route(Vec3d position) { this.position = position; }
    }
    private GuardianRitual() { }
    private static Vec3d station(Vec3d center,int index) {
        return GuardianAwakening.COMPOSITION.position(center, 1, index);
    }
    public static void start(ServerPlayerEntity player, GuardianWorldState.Village village) {
        if (village.ritualTicks != 0 || !GuardianVillages.eligible(player, village)) return;
        GuardianDialogue.stop(player);
        var attendants = availableAttendants(player.getServerWorld(), village, player.getPos());
        if (attendants.size() < 3) return;
        begin(player, village, attendants);
    }
    private static List<VillagerEntity> availableAttendants(ServerWorld world, GuardianWorldState.Village village, Vec3d position) {
        return GuardianVillages.villagers(world, village.pedestal).stream()
                .filter(v -> !v.isBaby() && !v.isAiDisabled() && v.getMainHandStack().isEmpty() && !assigned(v))
                .sorted(Comparator.comparingDouble(v -> v.squaredDistanceTo(position))).limit(3).toList();
    }
    public static String startCommand(ServerPlayerEntity player) {
        var village = GuardianVillages.claim(player);
        if (village == null) return "no_pedestal";
        if (!player.isAlive() || player.isSpectator()) return "not_ready";
        var state = RuneboundGuardian.state(player);
        if (village.ritualTicks > 0 || village.rebuildTicks > 0 || state.ritualTicks > 0 || state.detached
                || sscextras.drake.DrakeOutpostOwnership.get(player.getServer()).assignments().owner(player.getUuid()) != null
                || player.hasVehicle() || player.hasPassengers() || sscextras.drake.DrakeSoulbinding.ritualActive(player)
                || net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager.getPlayerTransformData(player).isTransforming) return "busy";
        if (RuneboundGuardian.stage(player) != 2 || !GuardianEquipment.complete(player)) return "guardian_required";
        var world = player.getServer().getWorld(village.world);
        if (world == null) return "pedestal_unavailable";
        world.getChunk(village.pedestal);
        if (!GuardianPedestal.intact(world, village.pedestal)) return "pedestal_unavailable";
        if (!world.isNearOccupiedPointOfInterest(village.pedestal)) return "village_required";
        GuardianDialogue.stop(player);
        var center = GuardianPedestal.position(village.pedestal);
        var attendants = new ArrayList<>(availableAttendants(world, village, center));
        while (attendants.size() < 3) {
            var villager = EntityType.VILLAGER.create(world);
            if (villager == null) return "pedestal_unavailable";
            var position = station(center, attendants.size());
            villager.refreshPositionAndAngles(position.x, position.y, position.z, 0, 0);
            villager.initialize(world, world.getLocalDifficulty(village.pedestal), SpawnReason.COMMAND, null, null);
            villager.setBreedingAge(0);
            villager.setPersistent();
            if (!world.spawnEntity(villager)) return "pedestal_unavailable";
            attendants.add(villager);
        }
        if (player.isSleeping()) player.wakeUp();
        player.teleport(world, center.x, center.y, center.z, 180, 0);
        player.clearActiveItem(); player.setVelocity(Vec3d.ZERO); player.fallDistance = 0; player.setOnGround(true);
        for (int i = 0; i < attendants.size(); i++) {
            var villager = attendants.get(i);
            var position = station(center, i);
            villager.getNavigation().stop();
            villager.refreshPositionAndAngles(position.x, position.y, position.z, 0, 0);
        }
        return begin(player, village, attendants) ? null : "busy";
    }
    private static boolean begin(ServerPlayerEntity player, GuardianWorldState.Village village, List<VillagerEntity> attendants) {
        GuardianVillages.stopRepair(player);
        village.attendants.clear(); attendants.forEach(v -> village.attendants.add(v.getUuid()));
        village.awakening = new sscextras.rituals.RitualProgress();
        village.awakeningRun = new GuardianAwakening(player, village);
        if (!village.awakeningRun.assign()) {
            village.awakeningRun.finish(); village.awakeningRun = null;
            village.awakening = null; village.attendants.clear(); return false;
        }
        village.ritualTicks = 1;
        stopEscort(player);
        var state = RuneboundGuardian.state(player); state.ritualTicks = 1; state.sync();
        if (player.isSleeping()) player.wakeUp();
        player.closeHandledScreen();
        player.sendMessage(Text.translatable("message.ssc-extras.guardian.ritual_begin"), false);
        GuardianWorldState.get(player.getServer()).markDirty();
        return true;
    }
    static boolean assigned(VillagerEntity villager) {
        return sscextras.drake.DrakeOutpostOwnership.get(villager.getServer()).assignments().owner(villager.getUuid()) != null
                || villager.getCommandTags().contains("ssc_extras_guardian_worker") || GuardianWorldState.get(villager.getServer()).villages.values().stream()
                .anyMatch(v -> (v.ritualTicks > 0 || v.rebuildTicks > 0) && v.attendants.contains(villager.getUuid()));
    }
    private static List<VillagerEntity> attendants(ServerWorld world, GuardianWorldState.Village village) {
        List<VillagerEntity> result = new ArrayList<>();
        for (var id : village.attendants) if (world.getEntity(id) instanceof VillagerEntity villager && villager.isAlive()) result.add(villager);
        return result;
    }
    public static void tick(ServerPlayerEntity player, GuardianWorldState.Village village) {
        if (village.ritualTicks <= 0) return;
        if (village.awakeningRun == null) {
            try { village.awakeningRun = new GuardianAwakening(player, village); }
            catch (IllegalArgumentException invalidCheckpoint) { cancel(player, village); return; }
        }
        var run = village.awakeningRun;
        var result = run.tick();
        if (result == sscextras.rituals.RitualStageRunner.Result.CANCELLED
                || result == sscextras.rituals.RitualStageRunner.Result.COMPLETE) {
            cancel(player, village);
            return;
        }
        var state = RuneboundGuardian.state(player);
        int previous = state.ritualTicks;
        village.ritualTicks = state.ritualTicks = run.ticks();
        if (previous <= 1 && state.ritualTicks > 1 || previous % 10 == 0) state.sync();
        GuardianWorldState.get(player.getServer()).markDirty();
    }
    static boolean walk(VillagerEntity villager, Vec3d target, double speed) {
        villager.addCommandTag("ssc_extras_guardian_worker");
        villager.setAiDisabled(false);
        if (villager.isSleeping()) villager.wakeUp();
        if (villager.squaredDistanceTo(target) < 1.44) {
            villager.getNavigation().stop(); villager.setMovementSpeed(0); ROUTES.remove(villager); return true;
        }
        var route = ROUTES.computeIfAbsent(villager, v -> new Route(v.getPos()));
        if (villager.squaredDistanceTo(route.position) > .25) { route.position = villager.getPos(); route.stuck = 0; }
        else route.stuck++;
        if (villager.age % 10 == 0 || villager.getNavigation().isIdle()) villager.getNavigation().startMovingTo(target.x, target.y, target.z, speed);
        openRouteDoors(villager);
        if (villager.horizontalCollision && villager.isOnGround() && target.y > villager.getY() + .3)
            villager.setVelocity(villager.getVelocity().add(0, .42, 0));
        if (route.stuck >= 100 && villager.squaredDistanceTo(target) < 16
                && villager.getWorld().getBlockState(BlockPos.ofFloored(target).down()).isSolidBlock(villager.getWorld(), BlockPos.ofFloored(target).down())
                && villager.getWorld().isSpaceEmpty(villager, villager.getDimensions(villager.getPose()).getBoxAt(target))) {
            villager.refreshPositionAndAngles(target.x, target.y, target.z, villager.getYaw(), 0);
            villager.getNavigation().stop(); ROUTES.remove(villager); return true;
        }
        return false;
    }
    private static void openRouteDoors(VillagerEntity villager) {
        var path = villager.getNavigation().getCurrentPath();
        if (path == null || path.isFinished()) return;
        var world = villager.getWorld();
        for (int i = Math.max(0, path.getCurrentNodeIndex() - 1); i <= Math.min(path.getLength() - 1, path.getCurrentNodeIndex() + 1); i++) {
            var pos = path.getNodePos(i);
            if (villager.squaredDistanceTo(Vec3d.ofBottomCenter(pos)) > 4) continue;
            var block = world.getBlockState(pos);
            if (block.getBlock() instanceof DoorBlock door && DoorBlock.canOpenByHand(block) && !block.get(DoorBlock.OPEN))
                door.setOpen(villager, world, block, pos, true);
        }
    }
    static void prop(VillagerEntity villager, Item item) {
        if (villager.getMainHandStack().isOf(item)) return;
        if (!villager.getMainHandStack().isEmpty() && !villager.getMainHandStack().getOrCreateNbt().getBoolean("GuardianProp")) return;
        var stack = new ItemStack(item); stack.getOrCreateNbt().putBoolean("GuardianProp", true);
        villager.equipStack(EquipmentSlot.MAINHAND, stack); villager.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0);
    }
    static void clearProp(VillagerEntity villager) {
        var held = villager.getMainHandStack();
        if (held.hasNbt() && held.getNbt().getBoolean("GuardianProp")) villager.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }
    private static void release(ServerWorld world, GuardianWorldState.Village village) {
        releaseRun(world, village);
        village.attendants.clear();
    }
    public static void releaseWorker(VillagerEntity villager) {
        clearProp(villager);villager.setAiDisabled(false);villager.getNavigation().stop();villager.setMovementSpeed(0);
        villager.removeScoreboardTag("ssc_extras_guardian_worker");
        villager.removeScoreboardTag("ssc_extras_guardian_repair"); ROUTES.remove(villager);
    }
    public static void suspend(ServerPlayerEntity player, GuardianWorldState.Village village) {
        var world=player.getServer().getWorld(village.world);
        releaseRun(world, village);
    }
    private static void releaseRun(ServerWorld world, GuardianWorldState.Village village) {
        if (village.awakeningRun != null) {
            village.awakeningRun.finish(); village.awakeningRun = null;
        } else if (world != null) {
            var assignments = sscextras.drake.DrakeOutpostOwnership.get(world.getServer()).assignments();
            for (var villager : attendants(world, village))
                if (assignments.owner(villager.getUuid()) == null) releaseWorker(villager);
        }
    }
    public static void cancel(ServerPlayerEntity player, GuardianWorldState.Village village) {
        stopEscort(player);
        var world = player.getServer().getWorld(village.world);
        release(world, village); village.awakening = null;
        if (world != null) {
            village.stoneCast.clear(world);
            if (village.body != null && world.getEntity(village.body) instanceof GuardianShellEntity body) body.work(0);
        }
        village.ritualTicks = 0; RuneboundGuardian.state(player).ritualTicks = 0; RuneboundGuardian.state(player).sync();
        GuardianWorldState.get(player.getServer()).markDirty();
    }
    public static void stopEscort(ServerPlayerEntity player) {
        var state=RuneboundGuardian.state(player);
        if(state.escort!=null) {state.escort.stop();state.escort=null;}
        if(state.ritualGuideId!=0) {state.ritualGuideId=0;state.sync();}
    }
    static void sound(ServerWorld world, Vec3d pos, net.minecraft.sound.SoundEvent sound, float pitch) {
        world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.NEUTRAL, .8f, pitch);
    }

    public static void rebuild(ServerPlayerEntity player, GuardianWorldState.Village village) {
        GuardianRecovery.tick(player, village);
    }
    public static void stopRecovery(ServerPlayerEntity player) { GuardianRecovery.stop(player); }
    static Vec3d safeStation(VillagerEntity villager, Vec3d center, int index) {
        return safeStation(villager, station(center, index));
    }
    static Vec3d safeStation(VillagerEntity villager, Vec3d desired) {
        var world = villager.getWorld();
        for (int radius = 0; radius <= 2; radius++) for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                var feet = BlockPos.ofFloored(desired).add(x, dy, z); var floor = feet.down();
                var at = new Vec3d(feet.getX() + .5, feet.getY(), feet.getZ() + .5);
                if (world.getWorldBorder().contains(feet) && world.getBlockState(floor).isSideSolidFullSquare(world, floor, net.minecraft.util.math.Direction.UP)
                        && world.getFluidState(feet).isEmpty() && world.isSpaceEmpty(villager, villager.getDimensions(villager.getPose()).getBoxAt(at))) return at;
            }
        }
        return null;
    }
}
