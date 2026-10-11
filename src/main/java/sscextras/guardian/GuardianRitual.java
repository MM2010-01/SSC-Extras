package sscextras.guardian;

import net.minecraft.block.DoorBlock;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
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
        return center.add(index==1?-2.5:index==2?2.5:.5,-.75,index==0?-2.5:-.5);
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
                || player.hasVehicle() || player.hasPassengers() || sscextras.drake.DrakeSoulbinding.ritualActive(player)
                || net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager.getPlayerTransformData(player).isTransforming) return "busy";
        if (RuneboundGuardian.stage(player) != 2 || !GuardianEquipment.complete(player)) return "guardian_required";
        var world = player.getServer().getWorld(village.world);
        if (world == null) return "pedestal_unavailable";
        world.getChunk(village.pedestal);
        if (!GuardianPedestal.intact(world, village.pedestal)) return "pedestal_unavailable";
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
        begin(player, village, attendants);
        return null;
    }
    private static void begin(ServerPlayerEntity player, GuardianWorldState.Village village, List<VillagerEntity> attendants) {
        GuardianVillages.stopRepair(player);
        village.attendants.clear(); attendants.forEach(v -> village.attendants.add(v.getUuid()));
        village.ritualTicks = 1;
        stopEscort(player);
        var state = RuneboundGuardian.state(player); state.ritualTicks = 1; state.sync();
        if (player.isSleeping()) player.wakeUp();
        player.closeHandledScreen();
        player.sendMessage(Text.translatable("message.ssc-extras.guardian.ritual_begin"), false);
        GuardianWorldState.get(player.getServer()).markDirty();
    }
    static boolean assigned(VillagerEntity villager) {
        return villager.getCommandTags().contains("ssc_extras_guardian_worker") || GuardianWorldState.get(villager.getServer()).villages.values().stream()
                .anyMatch(v -> (v.ritualTicks > 0 || v.rebuildTicks > 0) && v.attendants.contains(villager.getUuid()));
    }
    private static List<VillagerEntity> attendants(ServerWorld world, GuardianWorldState.Village village) {
        List<VillagerEntity> result = new ArrayList<>();
        for (var id : village.attendants) if (world.getEntity(id) instanceof VillagerEntity villager && villager.isAlive()) result.add(villager);
        return result;
    }
    public static void tick(ServerPlayerEntity player, GuardianWorldState.Village village) {
        var world = player.getServer().getWorld(village.world); if (world == null) return;
        var state = RuneboundGuardian.state(player);
        if (!GuardianPedestal.intact(world, village.pedestal)) { cancel(player, village); return; }
        var villagers = attendants(world, village);
        if (villagers.size() < 3) { cancel(player, village); return; }
        Vec3d center = GuardianPedestal.position(village.pedestal);
        int time = village.ritualTicks;
        if (time == 1) {
            var guide = villagers.get(0); prop(guide, Items.LEAD);
            if (state.escort == null) {
                walk(guide, player.getPos(), 1);
                guide.getLookControl().lookAt(player);
                if (player.squaredDistanceTo(guide) > 6.25 || !guide.canSee(player)) return;
                state.escort = new GuardianEscort(player, center);
                state.ritualGuideId = guide.getId();
                player.clearActiveItem(); player.closeHandledScreen(); player.setSprinting(false);
                player.getAbilities().flying = false; player.sendAbilitiesUpdate();
                state.sync();
            }
            Vec3d target = safeStation(guide, center, 0);
            if (target != null) walk(guide, target, 1);
            if (center.squaredDistanceTo(player.getPos()) > .64) {
                state.escort.guide();
                return;
            }
            village.ritualTicks = state.ritualTicks = time = 2;
            stopEscort(player);
            player.teleport(world, center.x, center.y, center.z, 180, 0); player.setOnGround(true);
            GuardianWorldState.get(player.getServer()).markDirty();
        }
        boolean ready = true;
        for (int i = 0; i < villagers.size(); i++) {
            var villager = villagers.get(i);
            Vec3d target = safeStation(villager, center, i);
            ready &= target != null && walk(villager, target, .8);
            villager.getLookControl().lookAt(center.x, center.y + 1.6, center.z);
        }
        if (!ready) return;
        if (time < SOUL_SEALED && !state.detached) {
            player.setVelocity(Vec3d.ZERO); player.setPosition(center); player.setYaw(180); player.setHeadYaw(180); player.bodyYaw = 180;
        }
        if (time < SOUL_START) {
            prop(villagers.get(1), Items.CHAIN); prop(villagers.get(2), Items.CHAIN);
            if (time == 40) sound(world, center, SoundEvents.BLOCK_CHAIN_PLACE, .7f);
        }
        if (time == SOUL_START) {
            prop(villagers.get(0), GuardianCores.INACTIVE_CORE);
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.ritual_soul"), false);
        }
        if (time >= SOUL_START && time < SOUL_SEALED) {
            Vec3d source = center.add(0, 1.5, 0), target = villagers.get(0).getPos().add(0, 1.2, 0);
            Vec3d soul = source.lerp(target, (time - SOUL_START) / (double)(SOUL_SEALED - SOUL_START));
            world.spawnParticles(ParticleTypes.SOUL, soul.x, soul.y, soul.z, 3, .10, .20, .10, .01);
            world.spawnParticles(ParticleTypes.ENCHANT, center.x, center.y + 1.5, center.z, 4, 1, 1, 1, .3);
            if (time % 30 == 0) sound(world, center, SoundEvents.ENTITY_VILLAGER_AMBIENT, .6f);
        }
        if (time == SOUL_SEALED) {
            var record = GuardianCores.create(player);
            var shell = GuardianCores.detach(player, false);
            if (shell == null) { cancel(player, village); return; }
            shell.work(1); village.body = shell.getUuid();
            var bearer = villagers.get(0);
            clearProp(bearer); bearer.equipStack(EquipmentSlot.MAINHAND, GuardianCores.stack(record));
            bearer.setEquipmentDropChance(EquipmentSlot.MAINHAND, 2);
            GuardianCores.observe(bearer.getMainHandStack(), bearer, "entity");
            sound(world, center, SoundEvents.BLOCK_BEACON_ACTIVATE, 1);
        }
        var shell = village.body == null ? null : world.getEntity(village.body) instanceof GuardianShellEntity body ? body : null;
        if (time >= SOUL_SEALED && time < SCULPTED && shell == null) { cancel(player, village); return; }
        if (time >= SOUL_SEALED && time < ENCASED) {
            shell.work(1 + (time - SOUL_SEALED) * 99 / (ENCASED - SOUL_SEALED));
            prop(villagers.get(1), LIQUID_STONE); prop(villagers.get(2), LIQUID_STONE);
            if (time % 5 == 0) world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST,
                    net.minecraft.block.Blocks.STONE.getDefaultState()), center.x, center.y + 2.7, center.z, 18, .45, .1, .45, .02);
            if (time == SOUL_SEALED + 1) player.sendMessage(Text.translatable("message.ssc-extras.guardian.ritual_stone"), false);
        }
        if (time >= ENCASED && time < SCULPTED) {
            shell.stage(3); shell.work(100 - (time - ENCASED) * 99 / (SCULPTED - ENCASED));
            for (int i = 1; i < 3; i++) {
                prop(villagers.get(i), CHISEL);
                if (time % 15 == 0) villagers.get(i).swingHand(Hand.MAIN_HAND);
            }
            if (time % 10 == 0) {
                sound(world, center, SoundEvents.BLOCK_STONE_BREAK, .5f);
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.STONE.getDefaultState()),
                        center.x, center.y + 1.3, center.z, 10, .7, 1, .7, .05);
            }
        }
        if (time == SCULPTED) {
            shell.work(0); shell.energy = 200; shell.setHealth(26);
            var bearer = villagers.get(0); var core = bearer.getMainHandStack();
            if (!Objects.equals(GuardianCores.id(core), state.coreId)) { cancel(player, village); return; }
            if (!GuardianCores.occupy(player, shell, core)) { cancel(player, village); return; }
            bearer.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            state.ritualTicks = time;
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.ritual_commands", state.guardianName), false);
        }
        if (time >= COMPLETE) {
            release(world, village); village.ritualTicks = 0; state.ritualTicks = 0; state.sync();
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.ritual_complete", state.guardianName), false);
        } else { village.ritualTicks++; state.ritualTicks = village.ritualTicks; if (time % 10 == 0) state.sync(); }
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
    private static void prop(VillagerEntity villager, Item item) {
        if (villager.getMainHandStack().isOf(item)) return;
        if (!villager.getMainHandStack().isEmpty() && !villager.getMainHandStack().getOrCreateNbt().getBoolean("GuardianProp")) return;
        var stack = new ItemStack(item); stack.getOrCreateNbt().putBoolean("GuardianProp", true);
        villager.equipStack(EquipmentSlot.MAINHAND, stack); villager.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0);
    }
    private static void clearProp(VillagerEntity villager) {
        var held = villager.getMainHandStack();
        if (held.hasNbt() && held.getNbt().getBoolean("GuardianProp")) villager.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }
    private static void release(ServerWorld world, GuardianWorldState.Village village) {
        for (var villager : attendants(world, village)) releaseWorker(villager);
        village.attendants.clear();
    }
    public static void releaseWorker(VillagerEntity villager) {
        clearProp(villager);villager.setAiDisabled(false);villager.getNavigation().stop();villager.setMovementSpeed(0);
        villager.removeScoreboardTag("ssc_extras_guardian_worker");
        villager.removeScoreboardTag("ssc_extras_guardian_repair"); ROUTES.remove(villager);
    }
    public static void suspend(ServerPlayerEntity player, GuardianWorldState.Village village) {
        var world=player.getServer().getWorld(village.world);
        if(world!=null) for(var villager:attendants(world,village)) releaseWorker(villager);
    }
    public static void cancel(ServerPlayerEntity player, GuardianWorldState.Village village) {
        stopEscort(player);
        var world = player.getServer().getWorld(village.world);
        if (world != null) release(world, village);
        village.ritualTicks = 0; RuneboundGuardian.state(player).ritualTicks = 0; RuneboundGuardian.state(player).sync();
        GuardianWorldState.get(player.getServer()).markDirty();
    }
    public static void stopEscort(ServerPlayerEntity player) {
        var state=RuneboundGuardian.state(player);
        if(state.escort!=null) {state.escort.stop();state.escort=null;}
        if(state.ritualGuideId!=0) {state.ritualGuideId=0;state.sync();}
    }
    private static void sound(ServerWorld world, Vec3d pos, net.minecraft.sound.SoundEvent sound, float pitch) {
        world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.NEUTRAL, .8f, pitch);
    }

    public static void rebuild(ServerPlayerEntity player, GuardianWorldState.Village village) {
        var record = GuardianCores.record(player);
        if (record == null || record.destroyed || !RuneboundGuardian.state(player).detached
                || RuneboundGuardian.state(player).ritualTicks > 0 || village != null && village.ritualTicks > 0) return;
        var world = player.getServer().getWorld(record.world); if (world == null) return;
        Entity holder = record.holder == null ? null : world.getEntity(record.holder);
        var held = holder instanceof ItemEntity item ? item.getStack()
                : holder instanceof VillagerEntity bearer && bearer.isAlive() ? bearer.getMainHandStack() : ItemStack.EMPTY;
        if (!record.id.equals(GuardianCores.id(held))) { stopRecovery(player); return; }
        Entity source = holder;
        if (village != null && record.world.equals(village.world) && village.bodyPos != null
                && village.bodyPos.isWithinDistance(holder.getPos(), GuardianEquipment.RANGE)) world.getChunk(village.bodyPos);
        var body = world.getEntitiesByClass(GuardianShellEntity.class, holder.getBoundingBox().expand(GuardianEquipment.RANGE),
                        shell -> shell.isAlive() && shell.stage() == 3 && shell.work() == 0 && shell.core.isEmpty()
                                && shell.squaredDistanceTo(source) <= GuardianEquipment.RANGE * GuardianEquipment.RANGE)
                .stream().min(Comparator.comparingDouble(shell -> shell.squaredDistanceTo(source))).orElse(null);
        if (body == null) {
            if (village == null || !record.world.equals(village.world)
                    || holder.squaredDistanceTo(GuardianPedestal.position(village.pedestal)) > GuardianEquipment.RANGE * GuardianEquipment.RANGE) {
                stopRecovery(player); return;
            }
            world.getChunk(village.pedestal);
            if (!GuardianPedestal.intact(world, village.pedestal)) { stopRecovery(player); return; }
            body = waitingBody(player, village, world, GuardianPedestal.position(village.pedestal));
            if (body == null) { stopRecovery(player); return; }
        }
        Vec3d center = body.getPos();
        record.recoveryTicks++;
        GuardianWorldState.get(player.getServer()).markDirty();
        if (village != null) { village.recoveryTicks = record.recoveryTicks; village.rebuildTicks = Math.max(1, record.insertionTicks); }
        if (holder instanceof ItemEntity item) {
            var residents = world.getEntitiesByClass(VillagerEntity.class, item.getBoundingBox().expand(GuardianEquipment.RANGE),
                    v -> v.isAlive() && !v.isBaby() && !v.isAiDisabled() && v.getMainHandStack().isEmpty()
                            && (!assigned(v) || v.getUuid().equals(record.rescuer)
                                || village != null && village.attendants.contains(v.getUuid())));
            var rescuer = residents.stream()
                    .min(Comparator.comparingInt((VillagerEntity v) -> v.getUuid().equals(record.rescuer) ? 0 : 1)
                            .thenComparingDouble(v -> v.squaredDistanceTo(item))).orElse(null);
            boolean fallback = record.recoveryTicks >= 300;
            if (rescuer == null && fallback) {
                rescuer = EntityType.VILLAGER.create(world);
                if (rescuer == null) return;
                Vec3d at = safeStation(rescuer, center, 0);
                if (at == null) return;
                rescuer.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
                rescuer.initialize(world, world.getLocalDifficulty(body.getBlockPos()), SpawnReason.EVENT, null, null);
                rescuer.setBreedingAge(0);
                rescuer.setPersistent();
                if (!world.spawnEntity(rescuer)) return;
            }
            if (rescuer == null) return;
            assignRescuer(player, record, rescuer);
            if (!fallback && !walk(rescuer, item.getPos(), 1)) return;
            if (fallback && !recoveryCut(player, rescuer, center)) return;
            rescuer.equipStack(EquipmentSlot.MAINHAND, item.getStack());
            rescuer.setEquipmentDropChance(EquipmentSlot.MAINHAND, 2);
            item.setStack(ItemStack.EMPTY); item.discard();
            GuardianCores.observe(rescuer.getMainHandStack(), rescuer, "entity"); holder = rescuer;
            record.insertionTicks = 1;
        }
        var bearer = (VillagerEntity)holder;
        assignRescuer(player, record, bearer);
        GuardianCores.observe(bearer.getMainHandStack(), bearer, "entity");
        Vec3d at = safeStation(bearer, center, 0);
        if (at == null) return;
        if (!walk(bearer, at, 1)) {
            record.insertionTicks = 1;
            if (record.recoveryTicks >= 300) recoveryCut(player, bearer, center);
            return;
        }
        bearer.getLookControl().lookAt(center.x, center.y + 1.4, center.z);
        record.insertionTicks++;
        if (record.insertionTicks == 20) {
            bearer.swingHand(Hand.MAIN_HAND);
            sound(world, center, SoundEvents.BLOCK_BEACON_ACTIVATE, 1);
        }
        if (record.insertionTicks >= 40) {
            if (!GuardianCores.occupy(player, body, bearer.getMainHandStack())) return;
            bearer.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.rebuilt"), false);
        }
    }
    private static void assignRescuer(ServerPlayerEntity player, GuardianWorldState.Core record, VillagerEntity rescuer) {
        if (rescuer.getUuid().equals(record.rescuer)) return;
        releaseRescuer(player, record);
        record.rescuer = rescuer.getUuid();
        rescuer.addCommandTag("ssc_extras_guardian_worker");
    }
    private static void releaseRescuer(ServerPlayerEntity player, GuardianWorldState.Core record) {
        if (record.rescuer == null) return;
        for (var world : player.getServer().getWorlds())
            if (world.getEntity(record.rescuer) instanceof VillagerEntity rescuer) releaseWorker(rescuer);
        record.rescuer = null;
    }
    public static void stopRecovery(ServerPlayerEntity player) {
        var record = GuardianCores.record(player);
        if (record == null) return;
        var village = GuardianVillages.claim(player);
        boolean rebuilding = village != null && village.ritualTicks == 0 && village.rebuildTicks > 0;
        if (record.rescuer == null && record.recoveryTicks == 0 && record.insertionTicks == 0 && !rebuilding) return;
        releaseRescuer(player, record);
        record.recoveryTicks = record.insertionTicks = 0;
        if (rebuilding) {
            var world = player.getServer().getWorld(village.world);
            if (world != null) release(world, village);
            village.rebuildTicks = village.recoveryTicks = 0;
        }
        GuardianWorldState.get(player.getServer()).markDirty();
    }
    private static boolean recoveryCut(ServerPlayerEntity player, VillagerEntity bearer, Vec3d center) {
        Vec3d at = safeStation(bearer, center, 0); if (at == null) return false;
        var state = RuneboundGuardian.state(player); state.recoveryBlackout = 25; state.recoveryHintPending = true; state.sync();
        bearer.getNavigation().stop(); bearer.setVelocity(Vec3d.ZERO);
        bearer.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
        return true;
    }
    private static GuardianShellEntity waitingBody(ServerPlayerEntity player, GuardianWorldState.Village village, ServerWorld world, Vec3d center) {
        if (!world.isChunkLoaded(new net.minecraft.util.math.ChunkPos(village.pedestal).toLong())) return null;
        if (village.bodyPos != null) {
            world.getChunk(village.bodyPos);
            if (!world.isChunkLoaded(new net.minecraft.util.math.ChunkPos(village.bodyPos).toLong())) return null;
        }
        var body = village.body == null ? null : world.getEntity(village.body) instanceof GuardianShellEntity shell && shell.isAlive() ? shell : null;
        if (body != null && !body.core.isEmpty()) return null;
        if (body == null) {
            body = GuardianCores.SHELL.create(world);
            body.refreshPositionAndAngles(center.x, center.y, center.z, 180, 0);
            if (!world.isSpaceEmpty(body)) return null;
            body.formerOwner = player.getUuid(); body.stage(3); body.energy = 100; body.setHealth(26);
            if (!world.spawnEntity(body)) return null;
            village.body = body.getUuid();
        }
        body.work(0); village.bodyPos = body.getBlockPos();
        GuardianWorldState.get(player.getServer()).markDirty();
        return body;
    }
    private static Vec3d safeStation(VillagerEntity villager, Vec3d center, int index) {
        var desired = station(center, index); var world = villager.getWorld();
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
