package sscextras.guardian;

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
    private GuardianRitual() { }
    private static Vec3d station(Vec3d center,int index) {
        return center.add(index==1?-2.5:index==2?2.5:.5,-.75,index==0?-2.5:-.5);
    }
    public static void start(ServerPlayerEntity player, GuardianWorldState.Village village) {
        if (village.ritualTicks != 0 || !GuardianVillages.eligible(player, village)) return;
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
        village.attendants.clear(); attendants.forEach(v -> village.attendants.add(v.getUuid()));
        village.ritualTicks = 1;
        var state = RuneboundGuardian.state(player); state.ritualTicks = 1; state.sync();
        if (player.isSleeping()) player.wakeUp();
        player.closeHandledScreen();
        player.sendMessage(Text.translatable("message.ssc-extras.guardian.ritual_begin"), false);
        GuardianWorldState.get(player.getServer()).markDirty();
    }
    private static boolean assigned(VillagerEntity villager) {
        return GuardianWorldState.get(villager.getServer()).villages.values().stream()
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
            Vec3d goal = player.squaredDistanceTo(guide) > 9 ? player.getPos() : station(center,0);
            walk(guide, goal, 1);
            if (player.squaredDistanceTo(guide) <= 16 || state.escort != null) {
                Vec3d delta = center.subtract(player.getPos());
                if (delta.lengthSquared() > .64) {
                    if(state.escort==null) state.escort=new GuardianEscort(player,center);
                    state.escort.guide();
                    return;
                }
                stopEscort(player);
                player.teleport(world, center.x, center.y, center.z, 180, 0); player.setOnGround(true);
            } else return;
        }
        for (int i = 0; i < villagers.size(); i++) {
            var villager = villagers.get(i);
            Vec3d target = station(center,i);
            if (!walk(villager, target, .6)) return;
            villager.getLookControl().lookAt(center.x, center.y + 1.6, center.z);
        }
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
            GuardianCores.initialize(player);
            var record = GuardianCores.create(player);
            var shell = GuardianCores.detach(player, false);
            if (shell == null) { cancel(player, village); return; }
            shell.work(1); village.body = shell.getUuid();
            if (world.getEntity(record.holder) instanceof ItemEntity item) {
                clearProp(villagers.get(0)); villagers.get(0).equipStack(EquipmentSlot.MAINHAND, item.getStack());
                villagers.get(0).setEquipmentDropChance(EquipmentSlot.MAINHAND, 2); item.discard();
                GuardianCores.observe(villagers.get(0).getMainHandStack(), villagers.get(0), "entity");
            }
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
    private static boolean walk(VillagerEntity villager, Vec3d target, double speed) {
        villager.addCommandTag("ssc_extras_guardian_worker");
        villager.setAiDisabled(false);
        if (villager.squaredDistanceTo(target) < 1.44) { villager.getNavigation().stop(); villager.setMovementSpeed(0); return true; }
        if (villager.age % 10 == 0 || villager.getNavigation().isIdle()) villager.getNavigation().startMovingTo(target.x, target.y, target.z, speed);
        if (villager.horizontalCollision && villager.isOnGround() && target.y > villager.getY() + .3)
            villager.setVelocity(villager.getVelocity().add(0, .42, 0));
        return false;
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
    }
    private static void sound(ServerWorld world, Vec3d pos, net.minecraft.sound.SoundEvent sound, float pitch) {
        world.playSound(null, pos.x, pos.y, pos.z, sound, SoundCategory.NEUTRAL, .8f, pitch);
    }

    public static void rebuild(ServerPlayerEntity player, GuardianWorldState.Village village) {
        var record = GuardianCores.record(player);
        if (record == null || record.destroyed || !record.world.equals(village.world)) return;
        var world = player.getServer().getWorld(village.world); if (world == null) return;
        Entity holder = record.holder == null ? null : world.getEntity(record.holder);
        if (holder instanceof PlayerEntity || record.location.equals("block") || record.location.equals("shell")) {
            release(world,village); village.rebuildTicks=0; return;
        }
        if (!(holder instanceof ItemEntity) && !(holder instanceof VillagerEntity)) return;
        var residents = GuardianVillages.villagers(world, holder.getBlockPos());
        if (residents.isEmpty() && player.age % 100 == 0) {
            for (int i = 0; i < 3; i++) {
                double angle = i * Math.PI * 2 / 3;
                var at = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        holder.getBlockPos().add((int)(Math.cos(angle) * 32), 0, (int)(Math.sin(angle) * 32)));
                if (!world.getWorldBorder().contains(at) || !world.getBlockState(at.down()).isSolidBlock(world, at.down())) continue;
                var villager = EntityType.VILLAGER.create(world); villager.refreshPositionAndAngles(at, 0, 0);
                if (!world.isSpaceEmpty(villager)) continue;
                villager.initialize(world, world.getLocalDifficulty(at), SpawnReason.EVENT, null, null);
                villager.setPersistent(); world.spawnEntity(villager); residents.add(villager);
            }
        }
        if (residents.isEmpty()) return;
        if (holder instanceof ItemEntity item) {
            var rescuer = residents.stream().filter(v -> !v.isBaby() && v.getMainHandStack().isEmpty()
                            && (!assigned(v)||village.attendants.contains(v.getUuid())))
                    .min(Comparator.comparingDouble(v -> v.squaredDistanceTo(item))).orElse(null);
            if (rescuer == null) return;
            if(!village.attendants.contains(rescuer.getUuid())) village.attendants.add(rescuer.getUuid());
            village.rebuildTicks=Math.max(1,village.rebuildTicks);
            if (walk(rescuer, item.getPos(), 1)) {
                rescuer.equipStack(EquipmentSlot.MAINHAND, item.getStack()); rescuer.setEquipmentDropChance(EquipmentSlot.MAINHAND, 2);
                item.discard(); GuardianCores.observe(rescuer.getMainHandStack(), rescuer, "entity"); holder = rescuer;
            } else return;
        }
        var bearer = (VillagerEntity)holder;
        if (!Objects.equals(GuardianCores.id(bearer.getMainHandStack()), record.id)) return;
        GuardianCores.observe(bearer.getMainHandStack(), bearer, "entity");
        Vec3d center = GuardianPedestal.position(village.pedestal);
        Vec3d delta = center.subtract(bearer.getPos());
        if (delta.lengthSquared() > 16) {
            walk(bearer, bearer.getPos().add(delta.normalize().multiply(Math.min(16, delta.length()))), 1); return;
        }
        if (!GuardianPedestal.intact(world, village.pedestal)) { release(world,village); return; }
        var body = village.body == null ? null : world.getEntity(village.body) instanceof GuardianShellEntity shell && shell.isAlive() ? shell : null;
        if (body == null) {
            body = GuardianCores.SHELL.create(world); body.refreshPositionAndAngles(center.x, center.y, center.z, 180, 0);
            if (!world.isSpaceEmpty(body)) return;
            body.formerOwner = player.getUuid(); body.stage(3); body.work(100); body.energy = 100; body.setHealth(26);
            world.spawnEntity(body); village.body = body.getUuid(); village.rebuildTicks = 1;
        }
        village.attendants.removeIf(id->!(world.getEntity(id) instanceof VillagerEntity v)||!v.isAlive());
        if(!village.attendants.contains(bearer.getUuid())) village.attendants.add(bearer.getUuid());
        var carvers = GuardianVillages.villagers(world, village.pedestal).stream().filter(v -> !v.isBaby() && v != bearer
                        && (village.attendants.contains(v.getUuid()) || !assigned(v) && !v.isAiDisabled() && v.getMainHandStack().isEmpty()))
                .sorted(Comparator.comparingInt(v->village.attendants.contains(v.getUuid())?0:1)).limit(2).toList();
        for(var carver:carvers) if(!village.attendants.contains(carver.getUuid())) village.attendants.add(carver.getUuid());
        if (!walk(bearer, station(center,0), .7)) return;
        boolean ready = carvers.size()==2;
        for (int i = 0; i < carvers.size(); i++) {
            var carver = carvers.get(i); ready &= walk(carver, station(center,i+1), .7);
            prop(carver, CHISEL);
            if (player.age % 20 == 0) carver.swingHand(Hand.MAIN_HAND);
        }
        if (!ready) return;
        village.rebuildTicks++; body.work(Math.max(0, 100 - village.rebuildTicks / 6));
        if (player.age % 20 == 0) sound(world, center, SoundEvents.BLOCK_STONE_BREAK, .6f);
        if (village.rebuildTicks >= 600) {
            var core = bearer.getMainHandStack();
            body.work(0);
            if(!GuardianCores.occupy(player, body, core)) return;
            bearer.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY); release(world,village);
            village.rebuildTicks = 0; player.sendMessage(Text.translatable("message.ssc-extras.guardian.rebuilt"), false);
        }
        GuardianWorldState.get(player.getServer()).markDirty();
    }
}
