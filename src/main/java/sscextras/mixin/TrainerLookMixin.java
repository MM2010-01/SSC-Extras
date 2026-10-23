package sscextras.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.trainer.QuadrupedalTrainer;

@Mixin(Entity.class)
public abstract class TrainerLookMixin {
    @ModifyVariable(method = "setPitch", at = @At("HEAD"), argsOnly = true)
    private float trainerPitch(float pitch) {
        return (Object)this instanceof PlayerEntity player && QuadrupedalTrainer.neckLocked(player) ? QuadrupedalTrainer.clampPitch(player, pitch) : pitch;
    }
    @Inject(method = "changeLookDirection", at = @At("RETURN"))
    private void trainerMouse(double x, double y, CallbackInfo ci) {
        if ((Object)this instanceof PlayerEntity player && QuadrupedalTrainer.neckLocked(player)) {
            player.setPitch(QuadrupedalTrainer.clampPitch(player, player.getPitch()));
            player.prevPitch = QuadrupedalTrainer.clampPitch(player, player.prevPitch);
        }
    }
}
