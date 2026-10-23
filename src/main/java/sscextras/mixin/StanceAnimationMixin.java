package sscextras.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.*;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import sscextras.drake.DrakeQuadrupedAnimations;
import sscextras.player.PlayerStance;

@Mixin(value=AnimSystem.class, remap=false)
public abstract class StanceAnimationMixin {
    @Redirect(method="getAnimation", at=@At(value="INVOKE", target="Lnet/onixary/shapeShifterCurseFabric/player_form/PlayerFormBase;getAnimStateController"))
    private AbstractAnimStateController quadrupedalController(PlayerFormBase form, PlayerEntity player,
            AnimSystem.AnimSystemData data, Identifier state) {
        return PlayerStance.genericQuadruped(player) ? DrakeQuadrupedAnimations.humanoidController(player, data, state)
                : form.getAnimStateController(player, data, state);
    }
}
