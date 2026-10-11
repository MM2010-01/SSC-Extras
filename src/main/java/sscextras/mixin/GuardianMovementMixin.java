package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import sscextras.guardian.*;

@Mixin(PlayerEntity.class)
public abstract class GuardianMovementMixin {
    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d sscExtras$guardianMovement(Vec3d input) {
        var player = (PlayerEntity)(Object)this;
        var state = RuneboundGuardian.state(player);
        if (state.deactivated) { player.setVelocity(0, player.getVelocity().y, 0); return Vec3d.ZERO; }
        if (state.ritualTicks == 1 && state.escort != null) return state.escort.input();
        if (state.detached || state.ritualRestrained()) { player.setVelocity(Vec3d.ZERO); return Vec3d.ZERO; }
        return state.protecting ? state.brain == null ? Vec3d.ZERO : state.brain.movement() : input;
    }
}
