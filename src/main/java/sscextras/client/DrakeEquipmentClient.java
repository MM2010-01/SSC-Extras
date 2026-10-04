package sscextras.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.integration.origins.OriginsClient;
import sscextras.drake.*;

public final class DrakeEquipmentClient {
    private static boolean chestPressed;
    private static float lastSide, lastForward, lastYaw;
    private static boolean lastJump;
    private static Entity lastMount;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DrakeRiding.INPUT, (client, handler, buf, sender) -> {
            int rider = buf.readInt();
            float sideways = buf.readFloat(), forward = buf.readFloat(), yaw = buf.readFloat();
            boolean jump = buf.readBoolean();
            client.execute(() -> {
                if (client.player != null) ((DrakeRiding.State)client.player).sscExtras$setRiderInput(
                        new DrakeRiding.Input(sideways, forward, jump, yaw, client.world.getTime(), rider));
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var player = client.player;
            if (player == null) { lastMount = null; chestPressed = false; return; }
            boolean pressed = OriginsClient.useSecondaryActivePowerKeybind.isPressed();
            if (pressed && !chestPressed && client.currentScreen == null
                    && !DrakeEquipment.equipped(player, DrakeEquipment.RIDERS_CHEST).isEmpty())
                ClientPlayNetworking.send(DrakeRiding.CHEST, PacketByteBufs.create());
            chestPressed = pressed;
            Entity mount = player.getVehicle();
            // SSC does not consistently expose another player's form on the rider's client.
            // The server validates the current mount, permanent form, saddle and reins for every input.
            if (!(mount instanceof PlayerEntity) && !(mount instanceof StableDrakeEntity)) { lastMount = null; return; }
            float side = client.currentScreen == null ? player.input.movementSideways : 0;
            float forward = client.currentScreen == null ? player.input.movementForward : 0;
            boolean jump = client.currentScreen == null && player.input.jumping;
            if (mount != lastMount || side != lastSide || forward != lastForward || jump != lastJump
                    || Math.abs(player.getYaw() - lastYaw) > .5 || player.age % 10 == 0) {
                var packet = PacketByteBufs.create();
                packet.writeFloat(side); packet.writeFloat(forward); packet.writeFloat(player.getYaw()); packet.writeBoolean(jump);
                ClientPlayNetworking.send(DrakeRiding.INPUT, packet);
                lastMount = mount; lastSide = side; lastForward = forward; lastJump = jump; lastYaw = player.getYaw();
            }
        });
    }
}
