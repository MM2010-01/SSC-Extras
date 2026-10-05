package sscextras.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;
import sscextras.drake.*;

public final class DrakeCameraControl {
    private static ClientPlayerEntity owner;
    private static Perspective previousPerspective;
    private static boolean ritual, ridden;
    private static Vec3d position = Vec3d.ZERO, previousPosition = Vec3d.ZERO;
    private static float yaw, pitch, previousYaw, savedYaw, savedPitch;
    private static int mouseGrace;

    private DrakeCameraControl() { }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DrakeRitualTransform.RESET, (client, handler, buf, sender) -> {
            boolean resume = buf.readBoolean();
            client.execute(() -> {
                TransformManager.getPlayerTransformData(client.player).reset();
                TransformManager.executeClientTransformCompleteEffect();
                if (resume) InstinctTicker.isPausing = false;
            });
        });
        ClientTickEvents.START_CLIENT_TICK.register(DrakeCameraControl::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> restore(client));
    }

    private static void restore(MinecraftClient client) {
        if (ritual && previousPerspective != null) client.options.setPerspective(previousPerspective);
        if (owner != null && owner == client.player && (ritual || ridden)) {
            owner.setYaw(ritual ? savedYaw : yaw);
            owner.setPitch(ritual ? savedPitch : pitch);
            owner.prevYaw = owner.getYaw(); owner.prevPitch = owner.getPitch();
        }
        owner = null; previousPerspective = null; ritual = ridden = false;
    }

    private static void updateMode(MinecraftClient client) {
        var player = client.player;
        if (owner != player || player == null || !player.isAlive()) restore(client);
        if (player == null || !player.isAlive()) return;
        boolean held = DrakeSoulbinding.restrained(player);
        boolean mounted = !held && client.options.getPerspective().isFirstPerson()
                && player.getFirstPassenger() instanceof PillagerEntity rider && DrakeRiding.canControl(player, rider);
        if (ritual == held && ridden == mounted) return;
        restore(client);
        if (!held && !mounted) return;
        owner = player;
        ritual = held; ridden = mounted;
        savedYaw = yaw = previousYaw = player.getYaw(); savedPitch = pitch = player.getPitch();
        mouseGrace = 0;
        if (held) {
            previousPerspective = client.options.getPerspective();
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            var target = player.getPos().add(0, 1.5, 0);
            var offset = new Vec3d(-3.8, 1.5, 4.8).rotateY((float)-Math.toRadians(player.getYaw()));
            position = previousPosition = target.add(offset);
            yaw = previousYaw = (float)Math.toDegrees(Math.atan2(offset.x, -offset.z));
            pitch = (float)Math.toDegrees(Math.atan2(offset.y, Math.hypot(offset.x, offset.z)));
        }
    }

    private static void tick(MinecraftClient client) {
        updateMode(client);
        if (owner == null) return;
        previousYaw = yaw;
        if (ritual) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            previousPosition = position;
            if (client.currentScreen != null) return;
            var keys = client.options;
            double forward = (keys.forwardKey.isPressed() ? 1 : 0) - (keys.backKey.isPressed() ? 1 : 0);
            double side = (keys.leftKey.isPressed() ? 1 : 0) - (keys.rightKey.isPressed() ? 1 : 0);
            double up = (keys.jumpKey.isPressed() ? 1 : 0) - (keys.sneakKey.isPressed() ? 1 : 0);
            double angle = Math.toRadians(yaw);
            var movement = new Vec3d(-Math.sin(angle) * forward + Math.cos(angle) * side, up,
                    Math.cos(angle) * forward + Math.sin(angle) * side);
            if (movement.lengthSquared() > 0) position = position.add(movement.normalize().multiply(keys.sprintKey.isPressed() ? .5 : .2));
        } else {
            if (mouseGrace > 0) { mouseGrace--; return; }
            var input = DrakeRiding.input(owner);
            if (input != null) yaw += MathHelper.clamp(MathHelper.wrapDegrees(input.yaw() - yaw) * .025f, -1, 1);
        }
    }

    public static boolean look(Entity entity, double dx, double dy) {
        var client = MinecraftClient.getInstance();
        if (entity != client.player) return false;
        updateMode(client);
        if (!ritual && !ridden) return false;
        float turn = (float)dx * .15f;
        yaw += turn; previousYaw += turn;
        pitch = MathHelper.clamp(pitch + (float)dy * .15f, -90, 90);
        if (dx != 0 || dy != 0) mouseGrace = 4;
        return true;
    }

    public static boolean active(Entity entity) { return owner == entity && (ritual || ridden); }
    public static boolean free(Entity entity) { return owner == entity && ritual; }
    public static Vec3d position(float delta) { return previousPosition.lerp(position, delta); }
    public static float yaw(float delta) { return MathHelper.lerpAngleDegrees(delta, previousYaw, yaw); }
    public static float pitch() { return pitch; }
}
