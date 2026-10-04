package sscextras.drake;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class DrakeRiding {
    public static final Identifier INPUT = EarthenDrake.id("rider_input");
    public static final Identifier CHEST = EarthenDrake.id("open_riders_chest");

    public record Input(float sideways, float forward, boolean jump, float yaw, long tick, int rider) {
        public boolean moving() { return Math.abs(sideways) > .001f || Math.abs(forward) > .001f || jump; }
    }

    public interface State {
        Input sscExtras$getRiderInput();
        void sscExtras$setRiderInput(Input input);
        RiderChestInventory sscExtras$getChest();
        void sscExtras$setChest(RiderChestInventory inventory);
        boolean sscExtras$tracksDrakePassenger();
    }

    private DrakeRiding() { }

    public static boolean canCarryPillager(PlayerEntity player) {
        return player.isAlive() && EarthenDrake.stage(player) >= 2 && DrakeFaction.harnessed(player);
    }

    public static boolean accepts(PlayerEntity mount, Entity passenger) {
        return passenger instanceof PlayerEntity && DrakeEquipment.canRide(mount)
                || passenger instanceof net.minecraft.entity.mob.PillagerEntity && canCarryPillager(mount);
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
                ((State) mount).sscExtras$setRiderInput(input);
                if (mount instanceof ServerPlayerEntity owner) {
                    var packet = PacketByteBufs.create();
                    packet.writeInt(rider.getId());
                    packet.writeFloat(input.sideways());
                    packet.writeFloat(input.forward());
                    packet.writeFloat(input.yaw());
                    packet.writeBoolean(jump);
                    ServerPlayNetworking.send(owner, INPUT, packet);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(CHEST, (server, player, handler, buf, sender) -> server.execute(() -> {
            if (player.getVehicle() instanceof PlayerEntity mount && DrakeEquipment.canRide(mount))
                RiderChestInventory.open(player, mount);
            else RiderChestInventory.open(player, player);
        }));
    }

    public static boolean canControl(Entity mount, PlayerEntity rider) {
        return mount != null && mount.isAlive() && rider.isAlive() && !rider.isSpectator()
                && mount.getFirstPassenger() == rider && DrakeEquipment.canRide(mount) && DrakeEquipment.hasReins(mount);
    }

    public static Input input(Entity mount) {
        Input input = ((State) mount).sscExtras$getRiderInput();
        if (input == null || !input.moving() || mount.getWorld().getTime() - input.tick() > 20
                || !(mount.getFirstPassenger() instanceof PlayerEntity rider) || rider.getId() != input.rider()
                || !canControl(mount, rider)) return null;
        return input;
    }

    public static Vec3d movement(LivingEntity mount, Vec3d own) {
        Input input = input(mount);
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
