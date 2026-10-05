package sscextras.drake;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.Merchant;

public final class DrakeRiding {
    public static final Identifier INPUT = EarthenDrake.id("rider_input");
    public static final Identifier CHEST = EarthenDrake.id("open_riders_chest");
    public static final TagKey<net.minecraft.entity.EntityType<?>> HUMANOID_PASSENGERS = TagKey.of(
            RegistryKeys.ENTITY_TYPE, EarthenDrake.id("drake_humanoid_passengers"));

    public record Input(float sideways, float forward, boolean jump, float yaw, long tick, int rider, boolean sprint) {
        public Input(float sideways, float forward, boolean jump, float yaw, long tick, int rider) {
            this(sideways, forward, jump, yaw, tick, rider, false);
        }
        public boolean moving() { return Math.abs(sideways) > .001f || Math.abs(forward) > .001f || jump; }
    }

    public interface State {
        Input sscExtras$getRiderInput();
        void sscExtras$setRiderInput(Input input);
        RiderChestInventory sscExtras$getChest();
        void sscExtras$setChest(RiderChestInventory inventory);
        boolean sscExtras$tracksDrakePassenger();
        PillagerEntity sscExtras$battleRider();
        void sscExtras$battleRider(PillagerEntity rider);
        long sscExtras$nextPatrol();
        void sscExtras$nextPatrol(long tick);
    }

    private DrakeRiding() { }

    public static boolean mountForm(PlayerEntity player) {
        int stage = EarthenDrake.stage(player);
        return stage >= 2 || stage >= 0 && DrakeSoulbinding.bound(player);
    }

    public static boolean canCarryPillager(PlayerEntity player) {
        return player.isAlive() && mountForm(player) && DrakeFaction.harnessed(player);
    }

    public static boolean canCarryMob(PlayerEntity player) {
        return player.isAlive() && mountForm(player) && !DrakeEquipment.saddle(player).isEmpty();
    }

    public static boolean accepts(PlayerEntity mount, Entity passenger) {
        if (DrakeSoulbinding.role(mount) != 0) return false;
        if (passenger instanceof PlayerEntity) return DrakeEquipment.canRide(mount);
        if (passenger instanceof PillagerEntity) return canCarryPillager(mount);
        return passenger instanceof MobEntity && canCarryMob(mount)
                && (passenger instanceof Merchant || passenger.getType().isIn(HUMANOID_PASSENGERS));
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(INPUT, (server, rider, handler, buf, sender) -> {
            float sideways = buf.readFloat(), forward = buf.readFloat(), yaw = buf.readFloat();
            boolean jump = buf.readBoolean();
            if (!Float.isFinite(sideways) || !Float.isFinite(forward) || !Float.isFinite(yaw)) return;
            server.execute(() -> {
                Entity mount = rider.getVehicle();
                if (!canControl(mount, rider)) return;
                Input input = new Input(MathHelper.clamp(sideways, -1, 1), MathHelper.clamp(forward, -1, 1),
                        jump, MathHelper.wrapDegrees(yaw), mount.getWorld().getTime(), rider.getId());
                publishInput(mount, input);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(CHEST, (server, player, handler, buf, sender) -> server.execute(() -> {
            if (DrakeFeralization.restricted(player)) return;
            if (player.getVehicle() instanceof LivingEntity mount && DrakeEquipment.canRide(mount))
                RiderChestInventory.open(player, mount);
            else RiderChestInventory.open(player, player);
        }));
    }

    public static boolean canControl(Entity mount, LivingEntity rider) {
        if (mount == null || !mount.isAlive() || !rider.isAlive() || mount.getFirstPassenger() != rider
                || !(DrakeEquipment.canRide(mount) || rider instanceof PillagerEntity
                && mount instanceof PlayerEntity player && canCarryPillager(player)) || !DrakeEquipment.hasReins(mount)) return false;
        return rider instanceof PlayerEntity player && !player.isSpectator()
                || rider instanceof PillagerEntity pillager && !pillager.isAiDisabled()
                && (mount instanceof PlayerEntity drake && mountForm(drake) || mount instanceof StableDrakeEntity);
    }

    private static void publishInput(Entity mount, Input input) {
        Input previous = ((State) mount).sscExtras$getRiderInput();
        if (previous != null && previous.rider() == input.rider() && previous.sideways() == input.sideways()
                && previous.forward() == input.forward() && previous.jump() == input.jump() && previous.sprint() == input.sprint()
                && Math.abs(MathHelper.wrapDegrees(previous.yaw() - input.yaw())) < .5f && input.tick() - previous.tick() < 10) return;
        ((State) mount).sscExtras$setRiderInput(input);
        if (!(mount instanceof ServerPlayerEntity owner)) return;
        var packet = PacketByteBufs.create();
        packet.writeInt(input.rider());
        packet.writeFloat(input.sideways());
        packet.writeFloat(input.forward());
        packet.writeFloat(input.yaw());
        packet.writeBoolean(input.jump());
        packet.writeBoolean(input.sprint());
        ServerPlayNetworking.send(owner, INPUT, packet);
    }

    public static void tickPillager(PillagerEntity pillager) {
        if (!(pillager.getVehicle() instanceof LivingEntity mount)
                || !(mount instanceof PlayerEntity || mount instanceof StableDrakeEntity)) return;
        var enemy = pillager.getTarget();
        var path = pillager.getNavigation().getCurrentPath();
        var battle = DrakeBattleGoal.of(pillager);
        Vec3d direct = battle != null && battle.mount() == mount ? battle.directDestination() : null;
        float forward = 0, yaw = mount.getYaw();
        boolean jump = false, sprint = false;
        boolean moving = direct != null || battle != null && battle.mount() == mount && battle.pathing()
                || enemy != null && enemy.isAlive() && enemy != mount && !pillager.isTeammate(enemy);
        if (canControl(mount, pillager) && moving && (direct != null || path != null && !path.isFinished())) {
            if (direct == null) advancePath(path, mount);
            if (direct != null || !path.isFinished()) {
                var node = direct != null ? direct : path.getNodePosition(mount);
                double dx = node.x - mount.getX(), dz = node.z - mount.getZ();
                yaw = (float)(MathHelper.atan2(dz, dx) * 180 / Math.PI) - 90;
                forward = direct != null ? .8f : MathHelper.clamp((float)pillager.getMoveControl().getSpeed(), 0, 1);
                sprint = direct == null && path != null && path.getEnd() != null
                        && mount.squaredDistanceTo(Vec3d.ofBottomCenter(path.getTarget())) > 100
                        && (!(mount instanceof PlayerEntity player) || player.getHungerManager().getFoodLevel() > 6);
                if (sprint) forward = 1;
                jump = node.y > mount.getY() + 1 && dx * dx + dz * dz < 2.25;
            }
        }
        Input previous = ((State)mount).sscExtras$getRiderInput();
        if (forward != 0 || jump || previous != null && previous.rider() == pillager.getId() && previous.moving())
            publishInput(mount, new Input(0, forward, jump, yaw, mount.getWorld().getTime(), pillager.getId(), sprint));
    }

    public static void advancePath(net.minecraft.entity.ai.pathing.Path path, LivingEntity mount) {
        while (path != null && !path.isFinished()) {
            var node = path.getNodePosition(mount);
            double dx = node.x - mount.getX(), dz = node.z - mount.getZ();
            boolean descending = path.getCurrentNodeIndex() + 1 < path.getLength()
                    && path.getNode(path.getCurrentNodeIndex() + 1).y < node.y;
            if (dx * dx + dz * dz >= .36 || node.y > mount.getY() + .5 && !descending) break;
            path.next();
        }
    }

    public static Input input(Entity mount) {
        Input input = ((State) mount).sscExtras$getRiderInput();
        if (input == null || !input.moving() || mount.getWorld().getTime() - input.tick() > 20
                || !(mount.getFirstPassenger() instanceof LivingEntity rider) || rider.getId() != input.rider()
                || !canControl(mount, rider)) return null;
        return input;
    }

    public static Vec3d movement(LivingEntity mount, Vec3d own) {
        Input input = input(mount);
        if (mount.getFirstPassenger() instanceof PillagerEntity)
            mount.setSprinting(input != null && input.sprint());
        if (input == null) return own;
        mount.setYaw(input.yaw());
        mount.setHeadYaw(input.yaw());
        mount.bodyYaw = input.yaw();
        if (input.jump() && mount.isOnGround()) {
            if (mount instanceof PlayerEntity player) player.jump();
            else if (mount instanceof StableDrakeEntity drake) drake.riderJump();
        }
        return new Vec3d(input.sideways() * .5, own.y, input.forward() < 0 ? input.forward() * .25 : input.forward());
    }
}
