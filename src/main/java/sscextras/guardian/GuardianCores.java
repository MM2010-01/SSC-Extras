package sscextras.guardian;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import sscextras.collar.CuriosCompat;
import sscextras.drake.DrakeEquipment;
import java.util.*;
import java.util.function.Consumer;

public final class GuardianCores {
    public static final net.minecraft.util.Identifier GIVE_UP = RuneboundGuardian.id("guardian_give_up");
    public static final Item INACTIVE_CORE = new Item(new Item.Settings().maxCount(16));
    public static final GolemCoreItem CORE = new GolemCoreItem();
    public static final EntityType<GuardianShellEntity> SHELL = FabricEntityTypeBuilder.create(SpawnGroup.MISC, GuardianShellEntity::new)
            .dimensions(EntityDimensions.fixed(1.1f, 2.7f)).trackRangeBlocks(96).trackedUpdateRate(2).build();
    public static final EntityType<GuardianAnchorEntity> ANCHOR = FabricEntityTypeBuilder.create(SpawnGroup.MISC, GuardianAnchorEntity::new)
            .dimensions(EntityDimensions.fixed(.05f, .05f)).trackRangeBlocks(96).trackedUpdateRate(1).build();
    private static final Map<ServerPlayerEntity, GuardianAnchorEntity> ANCHORS = new WeakHashMap<>();
    private static final Set<UUID> TRANSFERRING = new HashSet<>();
    private static final UUID SLOT_VISIBILITY = UUID.fromString("eadf266f-0e09-4370-951b-6ab1ebc42ad1");
    private GuardianCores() { }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(GIVE_UP, (server, player, handler, buffer, sender) ->
                server.execute(() -> GuardianRecovery.giveUp(player)));
        Registry.register(Registries.ITEM, RuneboundGuardian.id("inactive_golem_core"), INACTIVE_CORE);
        Registry.register(Registries.ITEM, RuneboundGuardian.id("golem_core"), CORE);
        Registry.register(Registries.ENTITY_TYPE, RuneboundGuardian.id("guardian_shell"), SHELL);
        Registry.register(Registries.ENTITY_TYPE, RuneboundGuardian.id("guardian_anchor"), ANCHOR);
        FabricDefaultAttributeRegistry.register(SHELL, GuardianShellEntity.attributes());
        CuriosCompat.register(CORE);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player) || RuneboundGuardian.stage(player) != 3) return true;
            var state = RuneboundGuardian.state(player);
            var record = record(player);
            if (record != null && record.destroyed) { invalidate(player); return true; }
            if (state.detached) { player.setHealth(player.getMaxHealth()); return false; }
            if (record == null) initialize(player);
            breakBody(player); return false;
        });
        UseEntityCallback.EVENT.register((actor, world, hand, target, hit) -> {
            if (!(target instanceof ServerPlayerEntity guardian) || RuneboundGuardian.stage(guardian) != 3
                    || !(actor instanceof ServerPlayerEntity) || actor.isSpectator() || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            var held = actor.getStackInHand(hand);
            if (!held.isOf(CORE) || !valid(actor.getServer(), held) || GuardianCombat.locked(guardian)) return ActionResult.PASS;
            var soul = owner(actor.getServer(), held);
            if (soul == null || soul == guardian) return ActionResult.FAIL;
            var shell = detach(guardian, false);
            if (shell == null) return ActionResult.FAIL;
            shell.core = held.copyWithCount(1); held.decrement(1);
            occupy(soul, shell, shell.core); return ActionResult.SUCCESS;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            removeAnchor(handler.player); GuardianRecovery.suspend(handler.player);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> updateSlot(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> updateSlot(newPlayer));
    }

    public static void updateSlot(PlayerEntity player) {
        setSlotEnabled(player, RuneboundGuardian.stage(player) == 3);
    }

    public static void beforeSpawn(ServerPlayerEntity player) {
        updateSlot(player);
        if (CuriosCompat.instance == null && player.playerScreenHandler instanceof dev.emi.trinkets.TrinketPlayerScreenHandler handler)
            handler.trinkets$updateTrinketSlots(false);
    }

    static void setSlotEnabled(PlayerEntity player, boolean enabled) {
        if (player.getWorld().isClient) return;
        // Keep the registered capacity so existing saved cores survive inventory loading.
        double amount = enabled ? 0 : -1;
        if (CuriosCompat.instance != null) {
            CuriosCompat.instance.setSlotModifier(player, "core", SLOT_VISIBILITY, amount);
            return;
        }
        TrinketsApi.getTrinketComponent(player).ifPresent(component -> {
            var chest = component.getInventory().get("chest");
            var inventory = chest == null ? null : chest.get("core");
            if (inventory == null) return;
            var old = inventory.getModifiers().get(SLOT_VISIBILITY);
            if (old != null && old.getValue() == amount || old == null && amount == 0) return;
            inventory.removeModifier(SLOT_VISIBILITY);
            if (amount != 0) inventory.addPersistentModifier(new EntityAttributeModifier(SLOT_VISIBILITY,
                    "Guardian core slot", amount, EntityAttributeModifier.Operation.ADDITION));
            inventory.update();
            if (player instanceof ServerPlayerEntity serverPlayer && serverPlayer.networkHandler != null) {
                var buffer = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                var updates = new NbtCompound(); updates.put("chest/core", inventory.getSyncTag());
                buffer.writeInt(player.getId()); buffer.writeNbt(updates); buffer.writeNbt(new NbtCompound());
                ServerPlayNetworking.send(serverPlayer, dev.emi.trinkets.TrinketsNetwork.SYNC_INVENTORY, buffer);
            }
            if (player.playerScreenHandler instanceof dev.emi.trinkets.TrinketPlayerScreenHandler handler)
                handler.trinkets$updateTrinketSlots(false);
        });
    }

    public static UUID id(ItemStack stack) {
        return stack.isOf(CORE) && stack.hasNbt() && stack.getNbt().containsUuid("GuardianCore") ? stack.getNbt().getUuid("GuardianCore") : null;
    }
    public static GuardianWorldState.Core record(PlayerEntity player) {
        var id = RuneboundGuardian.state(player).coreId;
        return id == null || player.getServer() == null ? null : GuardianWorldState.get(player.getServer()).cores.get(id);
    }
    public static boolean valid(MinecraftServer server, ItemStack stack) {
        var id = id(stack); if (id == null || server == null) return false;
        var record = GuardianWorldState.get(server).cores.get(id);
        return record != null && !record.destroyed;
    }
    public static ServerPlayerEntity owner(MinecraftServer server, ItemStack stack) {
        if (!valid(server, stack)) return null;
        return server.getPlayerManager().getPlayer(GuardianWorldState.get(server).cores.get(id(stack)).owner);
    }
    public static ItemStack stack(GuardianWorldState.Core record) {
        var stack = new ItemStack(CORE); var nbt = stack.getOrCreateNbt();
        nbt.putUuid("GuardianCore", record.id); nbt.putUuid("SoulOwner", record.owner); nbt.putString("GuardianName", record.name); return stack;
    }
    public static ItemStack equipped(PlayerEntity player) {
        var io = DrakeEquipment.slots(); if (io == null) return ItemStack.EMPTY;
        var stacks = io.getEntitySlot(player, CuriosCompat.instance == null ? "chest" : "", "core");
        return stacks == null || stacks.isEmpty() ? ItemStack.EMPTY : stacks.get(0);
    }
    private static boolean equip(PlayerEntity player, ItemStack stack) {
        var io = DrakeEquipment.slots(); if (io == null) return false;
        String group = CuriosCompat.instance == null ? "chest" : "";
        var slots = io.getEntitySlot(player, group, "core");
        if (slots == null || slots.isEmpty()) return false;
        io.setEntitySlot(player, group, "core", 0, stack);
        player.currentScreenHandler.sendContentUpdates(); return true;
    }
    public static GuardianWorldState.Core create(ServerPlayerEntity player) {
        var current = record(player); if (current != null && !current.destroyed) return current;
        var data = GuardianWorldState.get(player.getServer()); var record = new GuardianWorldState.Core();
        record.id = UUID.randomUUID(); record.owner = player.getUuid(); record.holder = player.getUuid();
        record.world = player.getWorld().getRegistryKey(); record.pos = player.getBlockPos(); record.previousMode = player.interactionManager.getGameMode().getName();
        record.name = "Guardian #" + String.format(java.util.Locale.ROOT, "%02d", player.getRandom().nextInt(100)) + (char)('A' + player.getRandom().nextInt(26));
        data.cores.put(record.id, record); data.markDirty(); RuneboundGuardian.state(player).coreId = record.id; return record;
    }
    public static void initialize(ServerPlayerEntity player) {
        updateSlot(player);
        var record = create(player); var state = RuneboundGuardian.state(player);
        if (!equip(player, stack(record))) {
            throw new IllegalStateException("Runebound Guardian requires its registered core accessory slot");
        }
        state.guardianName = record.name; state.sync(); refreshName(player);
    }

    public static void tick(ServerPlayerEntity player) {
        if (!player.isAlive()) return;
        var state = RuneboundGuardian.state(player);
        if (state.detached && record(player) == null) {
            restoreControl(player, null);
            state.coreId = null;
            state.guardianName = "";
            state.sync(); refreshName(player);
        }
        if (RuneboundGuardian.stage(player) == 3 && state.coreId == null && !state.detached) initialize(player);
        var record = record(player); if (record == null) return;
        if (record.destroyed) { destroyOwner(player); return; }
        if (!state.detached && RuneboundGuardian.stage(player) == 3 && !TRANSFERRING.contains(player.getUuid())) {
            var socket = equipped(player);
            if (!record.id.equals(id(socket))) {
                var incoming = socket.copy(); equip(player, ItemStack.EMPTY);
                var shell = detach(player, false);
                if (shell != null && valid(player.getServer(), incoming)) {
                    shell.core = incoming; var soul = owner(player.getServer(), incoming);
                    if (soul != null) occupy(soul, shell, incoming);
                }
                return;
            }
            update(record, player.getServerWorld(), "body", player.getBlockPos(), player.getUuid());
        }
        if (!state.detached) return;
        if (state.ritualTicks > 1) {
            if (player.getCameraEntity() != player) player.setCameraEntity(player);
            removeAnchor(player);
            player.setVelocity(Vec3d.ZERO); player.fallDistance = 0;
            return;
        }
        follow(player, record);
        var claim = GuardianVillages.claim(player);
        if (claim == null || claim.ritualTicks == 0) GuardianRecovery.tick(player, claim);
    }

    public static GuardianShellEntity detach(ServerPlayerEntity player, boolean broken) {
        var state = RuneboundGuardian.state(player);
        if (state.detached || !TRANSFERRING.add(player.getUuid())) return null;
        try {
            var record = create(player);
            GuardianVillages.stopProtecting(player);
            GuardianVillages.stopRepair(player);
            if (player.isSleeping()) player.wakeUp();
            ItemStack physical = takeCore(player, record.id);
            player.closeHandledScreen();
            if (physical.isEmpty() && record.location.equals("body"))
                update(record, player.getServerWorld(), "item", player.getBlockPos(), null);
            GuardianShellEntity shell = broken ? null : SHELL.create(player.getServerWorld());
            var accessories = takeAccessories(player);
            if (broken) {
                player.getInventory().dropAll();
                for (int i = 0; i < accessories.size(); i++) player.dropItem(ItemStack.fromNbt(accessories.getCompound(i).getCompound("Stack")), true);
            } else {
                shell.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0);
                shell.formerOwner = player.getUuid(); shell.stage(RuneboundGuardian.stage(player)); shell.energy = state.energy;
                shell.weatherTicks = state.weatherTicks; shell.setHealth(Math.max(1, player.getHealth()));
                shell.setCustomName(player.getName()); shell.inventory = player.getInventory().writeNbt(new NbtList()); shell.accessories = accessories;
                player.getInventory().clear(); player.getServerWorld().spawnEntity(shell);
                var claim = GuardianVillages.claim(player);
                if (claim != null) { claim.body = shell.getUuid(); claim.bodyPos = shell.getBlockPos(); claim.recoveryTicks = 0; }
            }
            state.detached = true; state.overdrive = false; state.protecting = false; state.deactivated = false; state.statueMode = false;
            state.recoveryHintPending = false;
            state.traderRecoveryHint = false; state.canGiveUp = false; state.coreDeath = false;
            record.traderTicks = 0; record.traderReturned = false;
            record.previousMode = player.interactionManager.getGameMode().getName();
            player.setHealth(Math.max(1, player.getMaxHealth())); player.clearStatusEffects(); player.extinguish();
            player.changeGameMode(GameMode.SPECTATOR); player.setVelocity(Vec3d.ZERO); player.fallDistance = 0;
            if (!physical.isEmpty()) {
                var item = new ItemEntity(player.getWorld(), player.getX(), player.getY() + .5, player.getZ(), physical);
                item.setVelocity(Vec3d.ZERO); item.setNeverDespawn(); player.getWorld().spawnEntity(item); observe(physical, item, "item");
            }
            state.sync(); player.currentScreenHandler.syncState(); GuardianWorldState.get(player.getServer()).markDirty(); return shell;
        } finally { TRANSFERRING.remove(player.getUuid()); }
    }
    public static void breakBody(ServerPlayerEntity player) {
        var claim = GuardianVillages.claim(player); if (claim != null) { claim.body = null; claim.bodyPos = null; claim.rebuildTicks = claim.recoveryTicks = 0; }
        detach(player, true);
        player.getServerWorld().spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(net.minecraft.particle.ParticleTypes.BLOCK,
                net.minecraft.block.Blocks.STONE.getDefaultState()), player.getX(), player.getY() + 1, player.getZ(), 80, .5, 1, .5, .15);
        player.sendMessage(Text.translatable("message.ssc-extras.guardian.body_broken"), false);
    }

    public static boolean occupy(ServerPlayerEntity player, GuardianShellEntity shell, ItemStack core) {
        var record = record(player);
        if (record == null || !record.id.equals(id(core)) || record.destroyed || !RuneboundGuardian.state(player).detached
                || !shell.isAlive() || shell.work() != 0 || !shell.core.isEmpty() && !record.id.equals(id(shell.core))
                || !TRANSFERRING.add(player.getUuid())) return false;
        try {
            GuardianRecovery.stop(player);
            var state = RuneboundGuardian.state(player);
            player.setCameraEntity(player); removeAnchor(player);
            player.teleport((ServerWorld)shell.getWorld(), shell.getX(), shell.getY(), shell.getZ(), shell.getYaw(), 0);
            FormAbilityManager.applyForm(player, RuneboundGuardian.FORMS[3]);
            player.getInventory().readNbt(shell.inventory); restoreAccessories(player, shell.accessories);
            if (!equip(player, core.copyWithCount(1))) return false;
            state.detached = false; state.deactivated = false; state.statueMode = false; state.energy = Math.min(200, shell.energy); state.weatherTicks = shell.weatherTicks;
            state.canGiveUp = false;
            state.guardianName = record.name; state.ritualTicks = 0;
            player.changeGameMode(GameMode.byName(record.previousMode, GameMode.SURVIVAL));
            player.setHealth(Math.min(player.getMaxHealth(), Math.max(1, shell.getHealth()))); player.fallDistance = 0;
            update(record, player.getServerWorld(), "body", player.getBlockPos(), player.getUuid());
            shell.inventory = new NbtList(); shell.accessories = new NbtList(); shell.core = ItemStack.EMPTY; shell.discard();
            if (state.recoveryHintPending) {
                state.recoveryHintPending = false;
                player.sendMessage(Text.translatable("message.ssc-extras.guardian."
                        + (state.traderRecoveryHint ? "trader_village" : "blackout_recovery")), false);
            }
            state.traderRecoveryHint = false;
            state.sync(); refreshName(player); player.currentScreenHandler.syncState(); player.sendAbilitiesUpdate();
            var claim = GuardianVillages.claim(player); if (claim != null) { claim.body = null; claim.bodyPos = null; claim.rebuildTicks = claim.recoveryTicks = 0; }
            return true;
        } finally { TRANSFERRING.remove(player.getUuid()); }
    }
    private static ItemStack takeCore(ServerPlayerEntity player, UUID id) {
        ItemStack found = ItemStack.EMPTY;
        var socket = equipped(player);
        if (id.equals(id(socket))) { found = socket; equip(player, ItemStack.EMPTY); }
        var cursor = player.currentScreenHandler.getCursorStack();
        if (id.equals(id(cursor))) { found = cursor; player.currentScreenHandler.setCursorStack(ItemStack.EMPTY); }
        for (int i = 0; i < player.getInventory().size(); i++) if (id.equals(id(player.getInventory().getStack(i)))) {
            found = player.getInventory().removeStack(i);
        }
        return found;
    }

    private static NbtList takeAccessories(PlayerEntity player) {
        var result = new NbtList(); var io = DrakeEquipment.slots(); if (io == null) return result;
        var slots = CuriosCompat.instance != null ? CuriosCompat.instance.allSlots(player) : io.getEntitySlots(player);
        for (var entry : slots.entrySet()) for (int i = 0; i < entry.getValue().size(); i++) {
            var stack = entry.getValue().get(i); if (stack.isEmpty()) continue;
            var nbt = new NbtCompound(); nbt.putString("Group", entry.getKey().getLeft()); nbt.putString("Slot", entry.getKey().getRight());
            nbt.putInt("Index", i); nbt.put("Stack", stack.writeNbt(new NbtCompound())); result.add(nbt);
            io.setEntitySlot(player, entry.getKey().getLeft(), entry.getKey().getRight(), i, ItemStack.EMPTY);
        }
        return result;
    }
    private static void restoreAccessories(PlayerEntity player, NbtList list) {
        var io = DrakeEquipment.slots(); if (io == null) return;
        for (int i = 0; i < list.size(); i++) {
            var nbt = list.getCompound(i); var stack = ItemStack.fromNbt(nbt.getCompound("Stack"));
            var slots = io.getEntitySlot(player, nbt.getString("Group"), nbt.getString("Slot")); int index = nbt.getInt("Index");
            if (slots != null && index >= 0 && index < slots.size() && slots.get(index).isEmpty())
                io.setEntitySlot(player, nbt.getString("Group"), nbt.getString("Slot"), index, stack);
            else if (!player.getInventory().insertStack(stack)) player.dropItem(stack, false);
        }
    }

    public static void observe(ItemStack stack, Entity holder, String location) {
        if (!(holder.getWorld() instanceof ServerWorld world)
                || GuardianWorldState.get(world.getServer()).cores.isEmpty()) return;
        forEachCore(stack, core -> observeCore(core, holder, location, world), 0);
    }
    public static void observe(ItemEntity item) {
        if (!(item.getWorld() instanceof ServerWorld world)) return;
        forEachCore(item.getStack(), core -> {
            item.setNeverDespawn();
            observeCore(core, item, "item", world);
        }, 0);
    }
    private static void observeCore(ItemStack core, Entity holder, String location, ServerWorld world) {
        var record = GuardianWorldState.get(world.getServer()).cores.get(id(core));
        if (record != null && !record.destroyed && !bodyStillHolds(record, world.getServer()))
            update(record, world, location, holder.getBlockPos(), holder.getUuid());
    }
    public static void observe(BlockEntity block) {
        if (!(block.getWorld() instanceof ServerWorld world) || !(block instanceof Inventory inventory)
                || GuardianWorldState.get(world.getServer()).cores.isEmpty()) return;
        for (int i = 0; i < inventory.size(); i++) forEachCore(inventory.getStack(i), stack -> {
            var record = GuardianWorldState.get(world.getServer()).cores.get(id(stack));
            if (record != null && !record.destroyed && !bodyStillHolds(record, world.getServer()))
                update(record, world, "block", block.getPos(), null);
        }, 0);
    }
    private static boolean bodyStillHolds(GuardianWorldState.Core core, MinecraftServer server) {
        if (!core.location.equals("body")) return false;
        var player = server.getPlayerManager().getPlayer(core.owner);
        return player != null && !RuneboundGuardian.state(player).detached && core.id.equals(id(equipped(player)));
    }
    private static void update(GuardianWorldState.Core record, ServerWorld world, String location, BlockPos pos, UUID holder) {
        if (!record.world.equals(world.getRegistryKey()) || !record.pos.equals(pos) || !record.location.equals(location) || !Objects.equals(record.holder, holder)) {
            record.world = world.getRegistryKey(); record.pos = pos.toImmutable(); record.location = location; record.holder = holder;
            GuardianWorldState.get(world.getServer()).markDirty();
        }
        record.lastSeen = world.getTime();
    }
    public static void forEachCore(ItemStack stack, Consumer<ItemStack> callback, int depth) {
        if (stack.isEmpty() || depth > 16 || !stack.hasNbt()) return;
        if (id(stack) != null) { callback.accept(stack); return; }
        var tag = stack.getNbt();
        if (tag.contains("BlockEntityTag")) tag = tag.getCompound("BlockEntityTag");
        var items = tag.getList("Items", NbtElement.COMPOUND_TYPE);
        if (depth == 16) return;
        for (int i = 0; i < items.size(); i++) {
            var item = items.getCompound(i);
            if (item.contains("tag", NbtElement.COMPOUND_TYPE))
                forEachCore(ItemStack.fromNbt(item), callback, depth + 1);
        }
    }
    public static void destroyed(ItemStack stack, MinecraftServer server) {
        if (server == null) return;
        forEachCore(stack, core -> {
            var data = GuardianWorldState.get(server); var record = data.cores.get(id(core));
            if (record == null || record.destroyed || bodyStillHolds(record, server)) return;
            record.destroyed = true; data.markDirty();
            var player = server.getPlayerManager().getPlayer(record.owner); if (player != null) destroyOwner(player);
        }, 0);
    }
    static void destroyOwner(ServerPlayerEntity player) {
        var record = record(player); if (record == null) return;
        restoreControl(player, record);
        RuneboundGuardian.state(player).coreDeath = true;
        RuneboundGuardian.state(player).sync();
        // Keep the destroyed record through ALLOW_DEATH so true core death cannot break another body.
        player.damage(player.getDamageSources().genericKill(), Float.MAX_VALUE);
    }
    private static void invalidate(ServerPlayerEntity player) {
        GuardianRecovery.stop(player);
        var record = record(player);
        if (record != null) {
            record.destroyed = true; GuardianWorldState.get(player.getServer()).markDirty();
            takeCore(player, record.id);
        }
        restoreControl(player, record);
        var state = RuneboundGuardian.state(player);
        state.coreId = null; state.guardianName = "";
        state.sync(); refreshName(player);
    }
    private static void restoreControl(ServerPlayerEntity player, GuardianWorldState.Core record) {
        var village = GuardianVillages.claim(player);
        if (village != null && village.ritualTicks > 0) GuardianRitual.cancel(player, village);
        GuardianRecovery.stop(player);
        var state = RuneboundGuardian.state(player);
        if (state.detached) {
            player.setCameraEntity(player); removeAnchor(player);
            if (player.isSpectator()) player.changeGameMode(GameMode.byName(record == null ? "survival" : record.previousMode, GameMode.SURVIVAL));
            player.setVelocity(Vec3d.ZERO); player.fallDistance = 0;
        }
        state.detached = false; state.deactivated = false; state.statueMode = false; state.ritualTicks = 0; state.ritualGuideId = 0;
        state.recoveryBlackout = 0; state.recoveryHintPending = false;
        state.traderRecoveryHint = false; state.canGiveUp = false;
    }
    public static void revert(ServerPlayerEntity player) {
        var state = RuneboundGuardian.state(player); var record = record(player);
        GuardianRecovery.stop(player);
        restoreControl(player, record);
        if (record != null) record.destroyed = true;
        state.coreId = null; state.guardianName = "";
        if (id(equipped(player)) != null) equip(player, ItemStack.EMPTY);
        GuardianWorldState.get(player.getServer()).markDirty(); state.sync(); refreshName(player);
    }

    private static void refreshName(ServerPlayerEntity player) {
        sscextras.player.PlayerNames.refresh(player);
    }

    private static void follow(ServerPlayerEntity player, GuardianWorldState.Core record) {
        var world = player.getServer().getWorld(record.world); if (world == null) return;
        // The soul's position keeps its current core chunk loaded, including hopper/chest transfers.
        if (player.getWorld() != world) { player.setCameraEntity(player); removeAnchor(player); player.teleport(world, record.pos.getX() + .5, record.pos.getY() + .5, record.pos.getZ() + .5, player.getYaw(), player.getPitch()); }
        Entity holder = record.holder == null ? null : world.getEntity(record.holder);
        if (holder == null && record.location.equals("entity") && record.holder != null) holder = player.getServer().getPlayerManager().getPlayer(record.holder);
        if (player.getUuid().equals(record.holder)) {
            // A displaced core can arrive from Curios after detachment. The soul must not carry itself.
            var physical = takeCore(player, record.id);
            holder = null;
            if (!physical.isEmpty()) {
                var item = new ItemEntity(world, player.getX(), player.getY(), player.getZ(), physical);
                item.setVelocity(Vec3d.ZERO); item.setNeverDespawn(); world.spawnEntity(item);
                observe(item); holder = item;
            }
        }
        if (holder != null && holder.getWorld() instanceof ServerWorld holderWorld && holderWorld != world) { update(record, holderWorld, record.location, holder.getBlockPos(), holder.getUuid()); return; }
        Vec3d target = holder == null ? Vec3d.ofCenter(record.pos).add(0, record.location.equals("block") ? .7 : 0, 0)
                : holder.getPos().add(0, holder instanceof LivingEntity ? holder.getStandingEyeHeight() : .25, 0);
        if (record.location.equals("block") && world.isChunkLoaded(record.pos)) {
            var block = world.getBlockEntity(record.pos); if (block != null) observe(block);
        }
        var anchor = ANCHORS.get(player);
        if (anchor == null || anchor.isRemoved() || anchor.getWorld() != world) {
            removeAnchor(player); anchor = ANCHOR.create(world); anchor.owner = player.getUuid(); anchor.setPosition(target); world.spawnEntity(anchor); ANCHORS.put(player, anchor);
        } else anchor.setPosition(target);
        anchor.source(holder);
        player.setVelocity(Vec3d.ZERO); player.fallDistance = 0;
        if (anchor.age >= 2 && player.getCameraEntity() != anchor) player.setCameraEntity(anchor);
    }
    private static void removeAnchor(ServerPlayerEntity player) {
        var anchor = ANCHORS.remove(player); if (anchor != null) anchor.discard();
    }
}
