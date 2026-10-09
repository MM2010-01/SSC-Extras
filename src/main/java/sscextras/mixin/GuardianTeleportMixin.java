package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.guardian.GuardianEquipment;

@Mixin(ServerPlayerEntity.class)
public abstract class GuardianTeleportMixin {
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void sscExtras$rememberGuardianStage(net.minecraft.entity.damage.DamageSource source, CallbackInfo ci) {
        GuardianEquipment.rememberDeath((ServerPlayerEntity)(Object)this);
    }
    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void sscExtras$cuffsTeleport(ServerWorld world, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        if (GuardianEquipment.outside((ServerPlayerEntity)(Object)this, world.getRegistryKey(), new Vec3d(x, y, z))) ci.cancel();
    }
    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void sscExtras$cuffsCommandTeleport(ServerWorld world, double x, double y, double z,
            java.util.Set<net.minecraft.network.packet.s2c.play.PositionFlag> flags, float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
        if (GuardianEquipment.outside((ServerPlayerEntity)(Object)this, world.getRegistryKey(), new Vec3d(x, y, z))) cir.setReturnValue(false);
    }
    @Inject(method = "moveToWorld", at = @At("HEAD"), cancellable = true)
    private void sscExtras$cuffsPortal(ServerWorld world, CallbackInfoReturnable<Entity> cir) {
        var player = (ServerPlayerEntity)(Object)this;
        if (GuardianEquipment.outside(player, world.getRegistryKey(), player.getPos())) cir.setReturnValue(null);
    }
}
