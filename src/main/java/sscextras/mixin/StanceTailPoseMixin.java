package sscextras.mixin;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.onixary.shapeShifterCurseFabric.render.form_render.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.client.EarthenDrakeAnimation;
import sscextras.client.FormTailPhysics;
import sscextras.player.PlayerStance;

/** Preserve native tail physics and the hip attachment on the shared four-limb pose. */
@Mixin(value = DefaultModelAnimationSystem.class, remap = false)
public abstract class StanceTailPoseMixin {
    @Inject(method = "processAnimation", at = @At("TAIL"))
    private void levelTail(FormRenderer formRenderer, FormModel model, PlayerEntityRenderer renderer,
            PlayerEntity player, float limbAngle, float limbDistance, float delta, float age,
            float yaw, float pitch, CallbackInfo ci) {
        var animation = (DefaultModelAnimationSystem)(Object)this;
        if (animation instanceof EarthenDrakeAnimation || !PlayerStance.genericQuadruped(player)) return;
        var body = model.getCachedGeoBone(animation.RM_BodyGeoBoneID);
        for (var chain : model.BCD_TailChain) if (!chain.isEmpty())
            FormTailPhysics.counterTilt(model.getCachedGeoBone(chain.get(0)), body);
    }
}
