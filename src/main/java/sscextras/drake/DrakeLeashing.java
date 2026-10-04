package sscextras.drake;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.LeadItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.UUID;

public final class DrakeLeashing {
    public interface State {
        Leash sscExtras$leash();
        int sscExtras$leashHolderId();
        void sscExtras$leashHolderId(int id);
    }

    private DrakeLeashing() { }
    public static boolean eligible(PlayerEntity player) {
        return player.isAlive() && !player.isSpectator() && EarthenDrake.stage(player) >= 2;
    }
    public static Entity holder(PlayerEntity player) {
        var state = (State)player;
        return player.getWorld().isClient ? player.getWorld().getEntityById(state.sscExtras$leashHolderId()) : state.sscExtras$leash().holder;
    }
    public static boolean attached(PlayerEntity player) {
        return player.getWorld().isClient ? ((State)player).sscExtras$leashHolderId() != 0 : ((State)player).sscExtras$leash().linked();
    }
    public static boolean attach(PlayerEntity player, Entity holder) {
        if (!eligible(player) || holder == player || !holder.isAlive() || holder.getWorld() != player.getWorld()) return false;
        var leash = ((State)player).sscExtras$leash();
        leash.holder = holder; leash.uuid = null; leash.fence = null; leash.waitTicks = 0;
        ((State)player).sscExtras$leashHolderId(holder.getId());
        return true;
    }
    public static void detach(PlayerEntity player, boolean drop) {
        var leash = ((State)player).sscExtras$leash();
        if (drop && leash.linked() && !player.getWorld().isClient) player.dropItem(Items.LEAD);
        leash.holder = null; leash.uuid = null; leash.fence = null; leash.waitTicks = 0;
        ((State)player).sscExtras$leashHolderId(0);
    }

    public static void register() {
        UseEntityCallback.EVENT.register((actor, world, hand, entity, hit) -> {
            if (actor.isSpectator()) return ActionResult.PASS;
            if (entity instanceof PlayerEntity drake && eligible(drake)) {
                if (holder(drake) == actor) {
                    if (!world.isClient) detach(drake, !actor.isCreative());
                    return ActionResult.SUCCESS;
                }
                var item = actor.getStackInHand(hand);
                if (item.isOf(Items.LEAD) && !attached(drake)) {
                    if (!world.isClient && attach(drake, actor) && !actor.isCreative()) item.decrement(1);
                    return ActionResult.SUCCESS;
                }
            }
            if (entity instanceof LeashKnotEntity knot) {
                if (tieHeld(actor, world, knot.getDecorationBlockPos())) return ActionResult.SUCCESS;
                if (!world.isClient) for (var drake : world.getEntitiesByClass(PlayerEntity.class, knot.getBoundingBox().expand(10),
                        player -> holder(player) == knot)) detach(drake, !actor.isCreative());
            }
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((actor, world, hand, hit) -> !actor.isSpectator()
                && world.getBlockState(hit.getBlockPos()).isIn(BlockTags.FENCES) && tieHeld(actor, world, hit.getBlockPos())
                ? ActionResult.SUCCESS : ActionResult.PASS);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof PlayerEntity player) detach(player, true);
        });
    }

    private static boolean tieHeld(PlayerEntity actor, World world, BlockPos pos) {
        var held = world.getEntitiesByClass(PlayerEntity.class, new Box(pos).expand(7), player -> holder(player) == actor);
        if (held.isEmpty()) return false;
        if (!world.isClient) {
            var knot = LeashKnotEntity.getOrCreate(world, pos);
            for (var player : held) attach(player, knot);
            LeadItem.attachHeldMobsToBlock(actor, world, pos);
            knot.onPlace();
        }
        return true;
    }

    public static final class Leash {
        private Entity holder;
        private UUID uuid;
        private BlockPos fence;
        private int waitTicks;
        private boolean linked() { return holder != null || uuid != null || fence != null; }

        public void tick(PlayerEntity player) {
            if (player.getWorld().isClient || !linked()) return;
            if (!eligible(player) || player.hasVehicle()) { detach(player, true); return; }
            if (holder == null) {
                if (fence != null && player.getWorld().getBlockState(fence).isIn(BlockTags.FENCES))
                    attach(player, LeashKnotEntity.getOrCreate(player.getWorld(), fence));
                else if (uuid != null) {
                    Entity found = ((ServerWorld)player.getWorld()).getEntity(uuid);
                    if (found != null) attach(player, found);
                }
                if (holder == null) { if (++waitTicks > 100) detach(player, true); return; }
            }
            if (!holder.isAlive() || holder.getWorld() != player.getWorld()) { detach(player, true); return; }
            Vec3d delta = holder.getPos().subtract(player.getPos());
            double distance = delta.length();
            if (distance > 10) { detach(player, true); return; }
            if (distance > (holder instanceof net.minecraft.entity.mob.PillagerEntity ? 3 : 6)) {
                Vec3d direction = delta.multiply(1 / distance);
                player.addVelocity(Math.copySign(direction.x * direction.x * .4, direction.x),
                        Math.copySign(direction.y * direction.y * .4, direction.y), Math.copySign(direction.z * direction.z * .4, direction.z));
                player.velocityModified = true;
                player.fallDistance = 0;
            }
        }

        public void write(NbtCompound nbt) {
            if (!linked()) return;
            var data = new NbtCompound();
            if (holder instanceof LeashKnotEntity knot) data.put("Fence", NbtHelper.fromBlockPos(knot.getDecorationBlockPos()));
            else if (holder != null) data.putUuid("Holder", holder.getUuid());
            else if (fence != null) data.put("Fence", NbtHelper.fromBlockPos(fence));
            else data.putUuid("Holder", uuid);
            nbt.put("DrakeLeash", data);
        }

        public void read(NbtCompound nbt) {
            holder = null; uuid = null; fence = null; waitTicks = 0;
            var data = nbt.getCompound("DrakeLeash");
            if (data.contains("Fence")) fence = NbtHelper.toBlockPos(data.getCompound("Fence"));
            else if (data.containsUuid("Holder")) uuid = data.getUuid("Holder");
        }
    }
}
