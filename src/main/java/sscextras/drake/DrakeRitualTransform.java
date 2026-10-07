package sscextras.drake;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.networking.ModPacketsS2CServer;
import net.onixary.shapeShifterCurseFabric.player_form.instinct.InstinctTicker;
import net.onixary.shapeShifterCurseFabric.player_form.transform.TransformManager;

public final class DrakeRitualTransform {
    public static final Identifier RESET = EarthenDrake.id("ritual_transform_reset");

    private DrakeRitualTransform() { }

    public static void recover(ServerPlayerEntity player) {
        var claim = DrakeOutpostOwnership.claim(player);
        if (claim != null && (claim.soulbound || claim.feral || claim.ritualCheckpoint != null) && claim.attendants.isEmpty()
                && !TransformManager.getPlayerTransformData(player).isTransforming) finish(player);
    }

    public static void finish(ServerPlayerEntity player) {
        TransformManager.getPlayerTransformData(player).reset();
        boolean anotherTransform = player.getServer().getPlayerManager().getPlayerList().stream()
                .anyMatch(other -> other != player && TransformManager.getPlayerTransformData(other).isTransforming);
        if (!anotherTransform) InstinctTicker.isPausing = false;
        var slow = player.getStatusEffect(StatusEffects.SLOWNESS);
        if (slow != null && (slow.getAmplifier() == 245 || slow.getAmplifier() == 200))
            player.removeStatusEffect(StatusEffects.SLOWNESS);
        ModPacketsS2CServer.sendTransformState(player, false, null, null);
        ModPacketsS2CServer.sendNoJumpTick(player, 0);
        var packet = PacketByteBufs.create();
        packet.writeBoolean(!anotherTransform);
        ServerPlayNetworking.send(player, RESET, packet);
    }
}
