package sscextras.player;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import sscextras.guardian.GuardianAnchorEntity;
import sscextras.guardian.GuardianCores;

/** Native spectator camera transport shared by detached cores and packaged players. */
public final class ItemBoundCamera {
    private static final Map<ServerPlayerEntity, GuardianAnchorEntity> ANCHORS = new WeakHashMap<>();
    private ItemBoundCamera() { }
    public static void follow(ServerPlayerEntity player, ServerWorld world, Vec3d position, Entity source) {
        if (player.getWorld() != world) {
            player.setCameraEntity(player); remove(player);
            player.teleport(world, position.x, position.y, position.z, player.getYaw(), player.getPitch());
        }
        var anchor = ANCHORS.get(player);
        if (anchor == null || anchor.isRemoved() || anchor.getWorld() != world) {
            remove(player); anchor = GuardianCores.ANCHOR.create(world);
            anchor.owner = player.getUuid(); anchor.setPosition(position); world.spawnEntity(anchor); ANCHORS.put(player, anchor);
        } else anchor.setPosition(position);
        anchor.source(source);
        player.setVelocity(Vec3d.ZERO); player.fallDistance = 0;
        if (anchor.age >= 2 && player.getCameraEntity() != anchor) player.setCameraEntity(anchor);
    }
    public static void remove(ServerPlayerEntity player) {
        var anchor = ANCHORS.remove(player); if (anchor != null) anchor.discard();
    }
    public static void release(ServerPlayerEntity player) { player.setCameraEntity(player); remove(player); }
}
