package sscextras.mixin;

import net.minecraft.entity.*;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import sscextras.petrification.Petrification;

@Mixin(Entity.class)
public abstract class PetrificationEntityMixin {
    @Shadow protected abstract void checkBlockCollision();
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void petrificationNoMovement(MovementType type, Vec3d motion, CallbackInfo ci) {
        if ((Object)this instanceof LivingEntity living && sscextras.statue.StatueBodies.locked(living)) {
            if (sscextras.statue.StatueBodies.falls(living) && type == MovementType.SELF) return;
            living.setVelocity(Vec3d.ZERO); checkBlockCollision(); ci.cancel();
        }
    }
    @ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
    private Vec3d petrificationVerticalOnly(Vec3d motion) {
        return (Object)this instanceof LivingEntity living && sscextras.statue.StatueBodies.falls(living)
                ? new Vec3d(0, Math.min(0, motion.y), 0) : motion;
    }
    @Inject(method = "startRiding(Lnet/minecraft/entity/Entity;Z)Z", at = @At("HEAD"), cancellable = true)
    private void petrificationNoMount(Entity vehicle, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof LivingEntity living && sscextras.statue.StatueBodies.locked(living)) cir.setReturnValue(false);
    }
}
