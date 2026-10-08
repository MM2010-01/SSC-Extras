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
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;
import java.util.UUID;

public final class DrakeLeashing {
    public interface State {
        Leash sscExtras$leash();
        int sscExtras$leashHolderId();
        void sscExtras$leashHolderId(int id);
    }

    private DrakeLeashing() { }
    public static boolean eligible(PlayerEntity player) {
        return player.isAlive() && !player.isSpectator()
                && (EarthenDrake.stage(player) >= 0 || originalWithReins(player));
    }
    public static boolean originalWithReins(PlayerEntity player) {
        var form = FormAbilityManager.getForm(player);
        return (form == RegPlayerForms.ORIGINAL_SHIFTER || form == RegPlayerForms.ORIGINAL_BEFORE_ENABLE)
                && !DrakeEquipment.equipped(player, DrakeEquipment.REINS).isEmpty();
    }
    public static Entity holder(PlayerEntity player) {
        var state = (State)player;
        return player.getWorld().isClient ? player.getWorld().getEntityById(state.sscExtras$leashHolderId()) : state.sscExtras$leash().holder;
    }
    public static boolean attached(PlayerEntity player) {
        return player.getWorld().isClient ? ((State)player).sscExtras$leashHolderId() != 0 : ((State)player).sscExtras$leash().linked();
    }
    public static float stepHeight(PlayerEntity player, float height) {
        if (QuadrupedMovement.quadrupedal(player)) return QuadrupedMovement.STEP_HEIGHT;
        return tautPillagerLead(player) ? Math.max(1, height) : height;
    }
    public static boolean tautPillagerLead(PlayerEntity player) {
        var holder = holder(player);
        if (!(holder instanceof net.minecraft.entity.mob.PillagerEntity) || !holder.isAlive()) return false;
        double distance = player.squaredDistanceTo(holder.getRootVehicle());
        double taut = DrakeSoulbinding.role(player) == DrakeSoulbinding.ESCORT ? 2.25 : 9;
        return distance > taut && distance <= 100;
    }
    public static boolean attach(PlayerEntity player, Entity holder) {
        if (!eligible(player) || holder == player || !holder.isAlive() || holder.getWorld() != player.getWorld()) return false;
        var leash = ((State)player).sscExtras$leash();
        var previous = leash.holder;
        leash.pillagerTied = holder instanceof net.minecraft.entity.mob.PillagerEntity || leash.linked() && leash.pillagerTied;
        leash.holder = holder; leash.uuid = null; leash.fence = null; leash.waitTicks = 0;
        leash.leadPath = null; leash.nextPath = 0;
        leash.recovery.reset();
        ((State)player).sscExtras$leashHolderId(holder.getId());
        if (previous != holder) removeEmptyKnot(previous);
        if (player.isSleeping()) player.wakeUp();
        refreshPosture(player);
        DrakeOutpostOwnership.registerLeashed(player);
        return true;
    }
    public static boolean attachPillager(PlayerEntity player, Entity holder) {
        var leash = ((State)player).sscExtras$leash();
        if (holder.getUuid().equals(leash.failedGuide) && player.getWorld().getTime() < leash.retryAfter) return false;
        if (!attach(player, holder)) return false;
        ((State)player).sscExtras$leash().pillagerTied = true;
        return true;
    }
    public static void detach(PlayerEntity player, boolean drop) {
        var leash = ((State)player).sscExtras$leash();
        var previous = leash.holder;
        if (drop && leash.linked() && !leash.pillagerTied && !player.getWorld().isClient
                && !MountMarket.get(player.getServer()).busy(player)) player.dropItem(Items.LEAD);
        leash.holder = null; leash.uuid = null; leash.fence = null; leash.waitTicks = 0;
        leash.leadPath = null;
        leash.recovery.reset();
        leash.pillagerTied = false;
        ((State)player).sscExtras$leashHolderId(0);
        removeEmptyKnot(previous);
        refreshPosture(player);
    }

    public static Vec3d recoveryDestination(PlayerEntity player, Entity guide) {
        if (player.getWorld().isClient || holder(player) != guide) return null;
        var leash = ((State)player).sscExtras$leash();
        return leash.recovery.phase() == sscextras.events.LeashRecovery.Phase.REGROUPING ? player.getPos() : null;
    }

    private static void removeEmptyKnot(Entity previous) {
        if (!(previous instanceof LeashKnotEntity knot) || knot.getWorld().isClient) return;
        if (knot.getWorld().getPlayers().stream().anyMatch(player -> holder(player) == knot)) return;
        if (!knot.getWorld().getEntitiesByClass(net.minecraft.entity.mob.MobEntity.class, knot.getBoundingBox().expand(10),
                mob -> mob.getHoldingEntity() == knot).isEmpty()) return;
        knot.discard();
    }

    private static void refreshPosture(PlayerEntity player) {
        if (!player.getWorld().isClient && EarthenDrake.stage(player) == 2)
            io.github.apace100.apoli.component.PowerHolderComponent.getPowers(player, DrakeBodyPower.class).forEach(DrakeBodyPower::refreshSize);
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
                if (holder(actor) == knot && sscextras.collar.TamingCollar.restricted(actor))
                    return sscextras.collar.TamingCollar.struggle(actor, hand);
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
        private boolean pillagerTied, legacy;
        private net.minecraft.entity.ai.pathing.Path leadPath;
        private long nextPath;
        private final sscextras.events.LeashRecovery recovery = new sscextras.events.LeashRecovery();
        private UUID failedGuide;
        private long retryAfter;
        private boolean linked() { return holder != null || uuid != null || fence != null; }

        public void tick(PlayerEntity player) {
            if (player.getWorld().isClient || !linked()) return;
            if (!eligible(player) || player.hasVehicle()) { detach(player, true); return; }
            if (holder == null) {
                if (legacy && fence != null) {
                    var claim = DrakeOutpostOwnership.claim(player);
                    if (claim != null && claim.world.equals(player.getWorld().getRegistryKey()) && claim.tie().equals(fence)) pillagerTied = true;
                }
                legacy = false;
                if (fence != null && player.getWorld().getBlockState(fence).isIn(BlockTags.FENCES))
                    attach(player, LeashKnotEntity.getOrCreate(player.getWorld(), fence));
                else if (uuid != null) {
                    Entity found = ((ServerWorld)player.getWorld()).getEntity(uuid);
                    if (found != null) attach(player, found);
                }
                if (holder == null) { if (++waitTicks > 100) detach(player, true); return; }
            }
            if (!holder.isAlive() || holder.getWorld() != player.getWorld()) { detach(player, true); return; }
            if (holder instanceof LeashKnotEntity knot) {
                var sale = MountMarket.get(player.getServer()).seller(player.getUuid());
                if (sale != null && !sale.sold() && sale.world().equals(player.getWorld().getRegistryKey())
                        && knot.getDecorationBlockPos().equals(sale.pen().add(6, 1, 7))
                        && !MountMerchantEntity.pen(sale.pen()).contains(player.getPos())) {
                    detach(player, false); return;
                }
            }
            var anchor = holder instanceof net.minecraft.entity.mob.PillagerEntity ? holder.getRootVehicle() : holder;
            Vec3d delta = anchor.getPos().subtract(player.getPos());
            double distance = delta.length();
            if (distance > 10) { detach(player, true); return; }
            boolean pillagerLead = holder instanceof net.minecraft.entity.mob.PillagerEntity;
            double slack = pillagerLead ? DrakeSoulbinding.role(player) == DrakeSoulbinding.ESCORT ? 1.5 : 3 : 6;
            if (pillagerLead && !DrakeSoulbinding.restrained(player)) {
                var phase = recovery.sample(player.age, player.getPos(), anchor.getPos(), distance > slack,
                        player.horizontalCollision || anchor.getY() > player.getY() + QuadrupedMovement.STEP_HEIGHT,
                        player.isOnGround() || player.isTouchingWater());
                if (phase == sscextras.events.LeashRecovery.Phase.FAILED) {
                    failedGuide = holder.getUuid(); retryAfter = player.getWorld().getTime() + 100;
                    detach(player, false); return;
                }
                if (phase == sscextras.events.LeashRecovery.Phase.REGROUPING) { leadPath = null; return; }
            }
            if (distance > slack) {
                Vec3d pull = delta;
                if (holder instanceof net.minecraft.entity.mob.PillagerEntity pillager
                        && (leadPath != null || player.horizontalCollision || anchor.getY() > player.getY() + .5)) {
                    if (player.getWorld().getTime() >= nextPath) {
                        nextPath = player.getWorld().getTime() + 10;
                        leadPath = ((DrakeStableNavigation)pillager.getNavigation()).leadPath(player);
                    }
                    DrakeRiding.advancePath(leadPath, player);
                    if (leadPath != null && !leadPath.isFinished()) pull = leadPath.getNodePosition(player).subtract(player.getPos());
                    else leadPath = null;
                }
                Vec3d direction = pull.normalize();
                player.addVelocity(pillagerLead ? direction.x * .4 : Math.copySign(direction.x * direction.x * .4, direction.x),
                        pillagerLead && !player.isTouchingWater() && !player.isInLava() ? 0 : Math.copySign(direction.y * direction.y * .4, direction.y),
                        pillagerLead ? direction.z * .4 : Math.copySign(direction.z * direction.z * .4, direction.z));
                player.velocityModified = true;
                player.fallDistance = 0;
            }
        }

        public void write(NbtCompound nbt) {
            if (!linked()) return;
            var data = new NbtCompound();
            data.putBoolean("PillagerTied", pillagerTied);
            if (holder instanceof LeashKnotEntity knot) data.put("Fence", NbtHelper.fromBlockPos(knot.getDecorationBlockPos()));
            else if (holder != null) data.putUuid("Holder", holder.getUuid());
            else if (fence != null) data.put("Fence", NbtHelper.fromBlockPos(fence));
            else data.putUuid("Holder", uuid);
            nbt.put("DrakeLeash", data);
        }

        public void read(NbtCompound nbt) {
            holder = null; uuid = null; fence = null; waitTicks = 0;
            leadPath = null; nextPath = 0;
            recovery.reset(); failedGuide = null; retryAfter = 0;
            var data = nbt.getCompound("DrakeLeash");
            pillagerTied = data.getBoolean("PillagerTied"); legacy = !data.contains("PillagerTied");
            if (data.contains("Fence")) fence = NbtHelper.toBlockPos(data.getCompound("Fence"));
            else if (data.containsUuid("Holder")) uuid = data.getUuid("Holder");
        }
    }
}
