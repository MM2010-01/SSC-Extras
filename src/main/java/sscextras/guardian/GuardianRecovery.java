package sscextras.guardian;

import net.minecraft.entity.*;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WanderingTraderManager;
import sscextras.mixin.GuardianSpawnerAccessor;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Consumer;

public final class GuardianRecovery {
    private GuardianRecovery() { }

    public static boolean tradersSpawn(ServerWorld world) {
        return ((GuardianSpawnerAccessor)world).sscExtras$spawners().stream().anyMatch(WanderingTraderManager.class::isInstance);
    }

    public static boolean canGiveUp(ServerPlayerEntity player) {
        var state = RuneboundGuardian.state(player);
        var core = GuardianCores.record(player);
        var world = core == null ? null : player.getServer().getWorld(core.world);
        return player.isAlive() && state.detached && state.ritualTicks == 0 && core != null && !core.destroyed
                && world != null && !tradersSpawn(world);
    }

    public static void giveUp(ServerPlayerEntity player) {
        if (!canGiveUp(player)) return;
        editCore(player, stack -> stack.getOrCreateNbt().putBoolean("Abandoned", true));
        player.sendMessage(Text.translatable("message.ssc-extras.guardian.give_up"), false);
        GuardianCores.record(player).destroyed = true;
        GuardianWorldState.get(player.getServer()).markDirty();
        GuardianCores.destroyOwner(player);
    }

    public static void tick(ServerPlayerEntity player, GuardianWorldState.Village binding) {
        var core = GuardianCores.record(player);
        var state = RuneboundGuardian.state(player);
        if (core == null) return;
        if (!player.isAlive() || core.destroyed || !state.detached || state.ritualTicks > 0 || binding != null && binding.ritualTicks > 0) {
            stop(player); return;
        }
        if (core.recoveryRun == null) core.recoveryRun = new GuardianCoreRecoveryEvent(player, core);
        core.recoveryRun.tick(binding);
        GuardianWorldState.get(player.getServer()).markDirty();
    }
    public static void suspend(ServerPlayerEntity player) {
        var core = GuardianCores.record(player);
        if (core != null && core.recoveryRun != null) {
            core.recoveryRun.finish(); core.recoveryRun = null;
        }
    }
    public static void stop(ServerPlayerEntity player) {
        var core = GuardianCores.record(player);
        if (core == null) return;
        suspend(player);
        core.rescuer = core.recoveryBody = null;
        core.recoveryTicks = core.insertionTicks = 0;
        var village = GuardianVillages.claim(player);
        if (village != null && village.ritualTicks == 0) village.rebuildTicks = village.recoveryTicks = 0;
        GuardianWorldState.get(player.getServer()).markDirty();
    }

    static boolean returnByTrader(ServerPlayerEntity player, GuardianWorldState.Village binding, GuardianCoreRecoveryEvent run) {
        var state = RuneboundGuardian.state(player);
        boolean giveUp = canGiveUp(player);
        if (state.canGiveUp != giveUp) { state.canGiveUp = giveUp; state.sync(); }
        var core = GuardianCores.record(player);
        if (giveUp || binding == null || core.traderReturned) {
            if (core.traderTicks != 0) { core.traderTicks = 0; GuardianWorldState.get(player.getServer()).markDirty(); }
            return false;
        }
        core.traderTicks = Math.min(300, core.traderTicks + 1);
        GuardianWorldState.get(player.getServer()).markDirty();
        if (core.traderTicks < 300) return false;
        var sourceWorld = player.getServer().getWorld(core.world);
        if (sourceWorld != null && core.holder != null && sourceWorld.getEntity(core.holder) instanceof VillagerEntity carrier
                && !run.available(carrier)) return false;
        var world = player.getServer().getWorld(binding.world);
        if (world == null) return false;
        world.getChunk(binding.pedestal);
        if (!GuardianPedestal.intact(world, binding.pedestal)) return false;
        var center = GuardianPedestal.position(binding.pedestal);
        if (world.isNearOccupiedPointOfInterest(binding.pedestal)) {
            var body = waitingBody(player, binding, world, center, run);
            if (body == null) return false;
            var bearer = GuardianVillages.villagers(world, binding.pedestal).stream()
                    .filter(v -> !v.isBaby() && !v.isAiDisabled() && v.getMainHandStack().isEmpty() && run.available(v) && (!GuardianRitual.assigned(v) || v.getUuid().equals(core.rescuer)))
                    .min(Comparator.comparingDouble(v -> v.squaredDistanceTo(body))).orElse(null);
            boolean created = bearer == null;
            if (created) bearer = EntityType.VILLAGER.create(world);
            if (bearer == null) return false;
            var at = GuardianRitual.safeStation(bearer, body.getPos(), 0);
            if (at == null) return false;
            if (created) {
                bearer.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
                bearer.initialize(world, world.getLocalDifficulty(body.getBlockPos()), SpawnReason.EVENT, null, null);
                bearer.setBreedingAge(0); bearer.setPersistent();
                if (!world.spawnEntity(bearer)) return false;
            }
            if (!run.worker(bearer)) { if (created) bearer.discard(); return false; }
            var receiver = bearer;
            if (!editCore(player, stack -> { receiver.equipStack(EquipmentSlot.MAINHAND, stack.copyWithCount(1)); stack.decrement(1); })) {
                run.resetWork(); if (created) bearer.discard();
                return false;
            }
            core.recoveryTicks = 0;
            bearer.getNavigation().stop(); bearer.setVelocity(Vec3d.ZERO);
            bearer.refreshPositionAndAngles(at.x, at.y, at.z, 0, 0);
            bearer.setEquipmentDropChance(EquipmentSlot.MAINHAND, 2);
            GuardianCores.observe(bearer.getMainHandStack(), bearer, "entity");
            core.insertionTicks = 1;
            binding.rebuildTicks = 1;
            state.recoveryHintPending = true; state.traderRecoveryHint = true;
        } else {
            var delivered = new boolean[1];
            editCore(player, stack -> {
                var item = new ItemEntity(world, center.x, center.y + .25, center.z, stack.copyWithCount(1));
                item.setVelocity(Vec3d.ZERO); item.setNeverDespawn(); item.setToDefaultPickupDelay();
                if (world.spawnEntity(item)) {
                    stack.decrement(1); GuardianCores.observe(item); delivered[0] = true;
                }
            });
            if (!delivered[0]) return false;
            run.resetWork();
            player.sendMessage(Text.translatable("message.ssc-extras.guardian.trader_pedestal"), false);
        }
        core.traderReturned = true; core.traderTicks = 0;
        state.recoveryBlackout = 25; state.canGiveUp = false; state.sync();
        return true;
    }

    static GuardianShellEntity waitingBody(ServerPlayerEntity player, GuardianWorldState.Village village, ServerWorld world, Vec3d center,
            GuardianCoreRecoveryEvent run) {
        if (!world.isChunkLoaded(new net.minecraft.util.math.ChunkPos(village.pedestal).toLong())) return null;
        if (village.bodyPos != null) {
            world.getChunk(village.bodyPos);
            if (!world.isChunkLoaded(new net.minecraft.util.math.ChunkPos(village.bodyPos).toLong())) return null;
        }
        var body = village.body == null ? null : world.getEntity(village.body) instanceof GuardianShellEntity shell && shell.isAlive() ? shell : null;
        if (body != null && body.squaredDistanceTo(center) > GuardianEquipment.RANGE * GuardianEquipment.RANGE) body = null;
        if (body != null && !body.core.isEmpty()) return null;
        if (body == null) {
            body = GuardianCores.SHELL.create(world);
            body.refreshPositionAndAngles(center.x, center.y, center.z, 180, 0);
            if (!world.isSpaceEmpty(body)) return null;
            body.formerOwner = player.getUuid(); body.stage(3); body.energy = 100; body.setHealth(26);
            if (!world.spawnEntity(body)) return null;
            village.body = body.getUuid();
        }
        if (!run.body(body)) return null;
        body.work(0); village.bodyPos = body.getBlockPos();
        GuardianWorldState.get(player.getServer()).markDirty();
        return body;
    }
    private static boolean editCore(ServerPlayerEntity player, Consumer<ItemStack> change) {
        var core = GuardianCores.record(player);
        var world = player.getServer().getWorld(core.world);
        if (world == null) return false;
        var holder = core.holder == null ? null : world.getEntity(core.holder);
        if (holder instanceof ItemEntity item) {
            var stack = item.getStack();
            if (!editStack(stack, core.id, change, 0)) return false;
            item.setStack(stack);
            if (stack.isEmpty()) item.discard();
            return true;
        }
        if (holder instanceof ServerPlayerEntity carrier) {
            var cursor = carrier.currentScreenHandler.getCursorStack();
            boolean changed = editStack(cursor, core.id, change, 0) || editInventory(carrier.getInventory(), core.id, change);
            if (changed) carrier.currentScreenHandler.syncState();
            return changed;
        }
        if (holder instanceof LivingEntity living) {
            for (var slot : EquipmentSlot.values()) {
                var stack = living.getEquippedStack(slot);
                if (editStack(stack, core.id, change, 0)) { living.equipStack(slot, stack); return true; }
            }
        }
        if (holder instanceof Inventory inventory && editInventory(inventory, core.id, change)) return true;
        if (core.location.equals("block")) {
            world.getChunk(core.pos);
            if (world.getBlockEntity(core.pos) instanceof Inventory inventory) return editInventory(inventory, core.id, change);
        }
        return false;
    }

    private static boolean editInventory(Inventory inventory, UUID id, Consumer<ItemStack> change) {
        for (int i = 0; i < inventory.size(); i++) {
            var stack = inventory.getStack(i);
            if (editStack(stack, id, change, 0)) { inventory.setStack(i, stack); inventory.markDirty(); return true; }
        }
        return false;
    }

    private static boolean editStack(ItemStack stack, UUID id, Consumer<ItemStack> change, int depth) {
        if (stack.isEmpty() || depth > 16 || !stack.hasNbt()) return false;
        if (id.equals(GuardianCores.id(stack))) { change.accept(stack); return true; }
        var tag = stack.getNbt();
        if (tag.contains("BlockEntityTag")) tag = tag.getCompound("BlockEntityTag");
        var items = tag.getList("Items", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < items.size() && depth < 16; i++) {
            var entry = items.getCompound(i); var child = ItemStack.fromNbt(entry);
            if (!editStack(child, id, change, depth + 1)) continue;
            if (child.isEmpty()) items.remove(i); else child.writeNbt(entry);
            return true;
        }
        return false;
    }
}
