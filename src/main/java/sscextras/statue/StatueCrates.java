package sscextras.statue;

import java.util.*;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import sscextras.golem.AbstractGolemForm;
import sscextras.guardian.*;
import sscextras.inventory.NestedItemStacks;
import sscextras.petrification.*;
import sscextras.player.ItemBoundCamera;
import static sscextras.statue.StatueWorldState.Status.*;

/** Server-owned transfer of physical statues. A player remains the same saved player throughout. */
public final class StatueCrates {
    public static final StatueCrateItem ITEM = new StatueCrateItem();
    private StatueCrates() { }
    public static UUID id(ItemStack stack) {
        return stack.isOf(ITEM) && stack.hasNbt() && stack.getNbt().containsUuid("StatueCrate") ? stack.getNbt().getUuid("StatueCrate") : null;
    }
    public static boolean packed(LivingEntity entity) { return Petrification.state(entity).crateId != null; }
    public static void register() {
        Registry.register(Registries.ITEM, Petrification.id("statue_package_crate"), ITEM);
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ITEM));
        UseEntityCallback.EVENT.register((actor, world, hand, target, hit) -> {
            var stack = actor.getStackInHand(hand);
            if (!stack.isOf(ITEM) || !(target instanceof LivingEntity living)) return ActionResult.PASS;
            if (id(stack) != null) return ActionResult.PASS;
            if (world.isClient) return StatueBodies.packable(living) ? ActionResult.SUCCESS : ActionResult.PASS;
            return actor instanceof ServerPlayerEntity player ? pack(player, living, hand) : ActionResult.PASS;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player; var occupant = StatueWorldState.get(server).player(player.getUuid());
            if (occupant != null) { Petrification.state(player).crateId = occupant.id; tick(player); }
            else if (packed(player)) { Petrification.state(player).crateId = null; PetrificationComponents.KEY.sync(player); }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ItemBoundCamera.remove(handler.player));
    }
    private static ActionResult fail(PlayerEntity actor, String key) {
        actor.sendMessage(Text.translatable("message.ssc-extras.statue_crate." + key), true); return ActionResult.FAIL;
    }
    public static ActionResult pack(ServerPlayerEntity actor, LivingEntity target, Hand hand) {
        var stack = actor.getStackInHand(hand);
        if (!stack.isOf(ITEM) || id(stack) != null || stack.getCount() != 1 || actor == target) return ActionResult.PASS;
        if (actor.isSpectator() || !actor.canModifyBlocks() || StatueBodies.locked(actor) || GuardianCombat.locked(actor)
                || actor.getWorld() != target.getWorld() || actor.squaredDistanceTo(target) > 36 || !actor.canSee(target)
                || !actor.getWorld().canPlayerModifyAt(actor, target.getBlockPos())) return ActionResult.FAIL;
        if (!StatueBodies.packable(target)) return fail(actor, "requires_statue");
        if (target instanceof PlayerEntity player && (RuneboundGuardian.state(player).ritualTicks > 0
                || RuneboundGuardian.state(player).detached || sscextras.drake.DrakeSoulbinding.ritualActive(player))) return fail(actor, "busy");
        var data = StatueWorldState.get(actor.getServer());
        if (target instanceof PlayerEntity && data.player(target.getUuid()) != null) return fail(actor, "busy");
        var o = new StatueWorldState.Occupant();
        o.id = UUID.randomUUID(); o.entity = target.getUuid(); o.player = target instanceof ServerPlayerEntity;
        o.name = target.getName().getString(); o.width = target.getWidth(); o.height = target.getHeight();
        o.world = actor.getWorld().getRegistryKey(); o.position = actor.getPos(); o.holder = actor.getUuid(); o.location = "entity";
        var pose = Petrification.state(target); pose.updateLock(); pose.writeToNbt(o.pose);
        if (target instanceof ServerPlayerEntity player) {
            o.mode = player.interactionManager.getGameMode().getName();
            player.clearActiveItem(); player.closeHandledScreen(); sscextras.player.PlayerControl.clear(player);
        } else if (!target.saveNbt(o.body)) return fail(actor, "unsupported");
        // The physical lead is returned on capture; do not resurrect its old attachment on unpacking.
        o.body.remove("Leash");
        data.add(o);
        var tag = stack.getOrCreateNbt(); tag.putUuid("StatueCrate", o.id); tag.putString("StatueName", o.name); tag.putBoolean("StatuePlayer", o.player);
        if (target instanceof ServerPlayerEntity player) {
            pose.crateId = o.id; player.changeGameMode(GameMode.SPECTATOR); PetrificationComponents.KEY.sync(player); tick(player);
            player.sendMessage(Text.translatable("message.ssc-extras.statue_crate.carried"), false);
        } else {
            if (target instanceof net.minecraft.entity.mob.MobEntity mob && mob.isLeashed()) mob.detachLeash(true, true);
            target.discard();
        }
        actor.getInventory().markDirty(); sound(actor.getServerWorld(), target.getPos(), true);
        return ActionResult.SUCCESS;
    }
    public static ActionResult place(ItemUsageContext context) {
        if (!(context.getWorld() instanceof ServerWorld world)) return id(context.getStack()) == null ? ActionResult.PASS : ActionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayerEntity actor) || id(context.getStack()) == null) return ActionResult.PASS;
        if (actor.isSpectator() || !actor.canModifyBlocks() || StatueBodies.locked(actor) || GuardianCombat.locked(actor)
                || !world.canPlayerModifyAt(actor, context.getBlockPos())) return ActionResult.FAIL;
        if (context.getSide() != Direction.UP) return fail(actor, "ground");
        var data = StatueWorldState.get(world.getServer()); var o = data.get(id(context.getStack()));
        if (o == null || o.status != PACKED) return fail(actor, "empty_reference");
        var position = context.getHitPos().add(0, .0001, 0);
        var box = new Box(position.x - o.width / 2, position.y, position.z - o.width / 2,
                position.x + o.width / 2, position.y + o.height, position.z + o.width / 2);
        if (position.y < world.getBottomY() || box.maxY > world.getTopY() || !world.getWorldBorder().contains(box)
                || !world.isSpaceEmpty(null, box) || !world.getOtherEntities(null, box, e -> e instanceof LivingEntity && !e.isSpectator()).isEmpty())
            return fail(actor, "space");
        float facing = (float)(MathHelper.atan2(actor.getZ() - position.z, actor.getX() - position.x) * 180 / Math.PI) - 90;
        if (o.player) {
            o.world = world.getRegistryKey(); o.position = position; o.yaw = facing; o.holder = null; o.status = RELEASED; data.markDirty();
            var player = world.getServer().getPlayerManager().getPlayer(o.entity);
            if (player != null) finish(player, o);
        } else {
            for (var dimension : world.getServer().getWorlds()) if (dimension.getEntity(o.entity) != null) return fail(actor, "busy");
            var entity = EntityType.loadEntityWithPassengers(o.body.copy(), world, e -> e);
            if (!(entity instanceof LivingEntity body)) return fail(actor, "unsupported");
            if (body instanceof GuardianShellEntity shell) shell.holdPlacedStatue();
            body.refreshPositionAndAngles(position.x, position.y, position.z, facing, body.getPitch());
            Petrification.state(body).face(facing);
            if (!world.spawnEntity(body)) return fail(actor, "space");
            data.remove(o);
        }
        var tag = context.getStack().getNbt(); tag.remove("StatueCrate"); tag.remove("StatueName"); tag.remove("StatuePlayer");
        actor.getInventory().markDirty(); sound(world, position, false); return ActionResult.SUCCESS;
    }
    public static void tick(ServerPlayerEntity player) {
        var state = Petrification.state(player); if (state.crateId == null) return;
        var o = StatueWorldState.get(player.getServer()).get(state.crateId);
        if (o == null || !o.player || !o.entity.equals(player.getUuid())) { state.crateId = null; PetrificationComponents.KEY.sync(player); return; }
        if (o.status != PACKED) { finish(player, o); return; }
        if (!player.isSpectator()) player.changeGameMode(GameMode.SPECTATOR);
        var world = player.getServer().getWorld(o.world); if (world == null) return;
        Entity holder = o.holder == null ? null : world.getEntity(o.holder);
        if (holder == null && o.holder != null) holder = player.getServer().getPlayerManager().getPlayer(o.holder);
        if (holder != null && holder != player && !holder.isRemoved() && holder.getWorld() instanceof ServerWorld holderWorld) {
            update(o, holderWorld, holder.getPos(), holder.getUuid(), o.location); world = holderWorld;
        } else holder = null;
        var position = o.position.add(0, holder instanceof LivingEntity living ? living.getStandingEyeHeight() : .5, 0);
        ItemBoundCamera.follow(player, world, position, holder);
    }
    private static void finish(ServerPlayerEntity player, StatueWorldState.Occupant o) {
        var world = player.getServer().getWorld(o.world); if (world == null) return;
        ItemBoundCamera.release(player);
        // Keep the packed marker through mode/teleport hooks so they cannot resume a half-restored body.
        player.teleport(world, o.position.x, o.position.y, o.position.z, o.yaw, o.pose.getFloat("Pitch"));
        player.changeGameMode(GameMode.byName(o.mode, GameMode.SURVIVAL));
        var state = Petrification.state(player); state.readFromNbt(o.pose.copy()); state.crateId = null;
        state.face(o.yaw); player.setVelocity(Vec3d.ZERO); player.fallDistance = 0;
        StatueWorldState.get(player.getServer()).remove(o);
        PetrificationComponents.KEY.sync(player);
        if (o.status == DESTROYED) {
            if (RuneboundGuardian.stage(player) == 3) GuardianCores.destroyPackagedSoul(player);
            else player.damage(player.getDamageSources().genericKill(), Float.MAX_VALUE);
        }
    }
    private static void update(StatueWorldState.Occupant o, ServerWorld world, Vec3d position, UUID holder, String location) {
        if (o.status != PACKED) return;
        if (!o.world.equals(world.getRegistryKey()) || !o.position.equals(position) || !Objects.equals(o.holder, holder) || !o.location.equals(location)) {
            o.world = world.getRegistryKey(); o.position = position; o.holder = holder; o.location = location;
            StatueWorldState.get(world.getServer()).markDirty();
        }
    }
    public static void observe(ItemStack stack, Entity holder) {
        if (!(holder.getWorld() instanceof ServerWorld world) || StatueWorldState.get(world.getServer()).isEmpty()) return;
        NestedItemStacks.visit(stack, item -> id(item) != null, item -> {
            var o = StatueWorldState.get(world.getServer()).get(id(item));
            if (o != null && !o.entity.equals(holder.getUuid())) update(o, world, holder.getPos(), holder.getUuid(), "entity");
        }, 0);
    }
    public static void observe(BlockEntity block) {
        if (!(block.getWorld() instanceof ServerWorld world) || !(block instanceof Inventory inventory) || StatueWorldState.get(world.getServer()).isEmpty()) return;
        for (int i = 0; i < inventory.size(); i++) NestedItemStacks.visit(inventory.getStack(i), item -> id(item) != null, item -> {
            var o = StatueWorldState.get(world.getServer()).get(id(item));
            if (o != null) update(o, world, Vec3d.ofCenter(block.getPos()), null, "block");
        }, 0);
    }
    public static void destroyed(ItemStack stack, MinecraftServer server) {
        if (server == null || StatueWorldState.get(server).isEmpty()) return;
        NestedItemStacks.visit(stack, item -> id(item) != null, item -> {
            var data = StatueWorldState.get(server); var o = data.get(id(item));
            if (o == null || o.status != PACKED) return;
            if (!o.player) { data.remove(o); return; }
            o.status = DESTROYED; data.markDirty();
            var player = server.getPlayerManager().getPlayer(o.entity); if (player != null) finish(player, o);
        }, 0);
    }
    private static void sound(ServerWorld world, Vec3d pos, boolean pack) {
        world.playSound(null, pos.x, pos.y, pos.z, pack ? net.minecraft.sound.SoundEvents.BLOCK_WOOL_PLACE : net.minecraft.sound.SoundEvents.BLOCK_STONE_PLACE,
                net.minecraft.sound.SoundCategory.PLAYERS, 1, 1);
    }
}
