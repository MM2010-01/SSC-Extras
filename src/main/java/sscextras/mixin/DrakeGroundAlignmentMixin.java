package sscextras.mixin;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.onixary.shapeShifterCurseFabric.player_animation.v3.AnimSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.EarthenDrake;

@Mixin(PlayerEntityRenderer.class)
public abstract class DrakeGroundAlignmentMixin {
    @Inject(method = "scale(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V",
            at = @At("TAIL"))
    private void sscExtras$alignPaws(AbstractClientPlayerEntity player, MatrixStack matrices,
            float tickDelta, CallbackInfo ci) {
        int stage = EarthenDrake.stage(player);
        if ((stage != 2 && (stage < 0 || stage > 2 || !sscextras.drake.DrakeSoulbinding.bound(player)))
                || sscextras.drake.DrakeSoulbinding.shoeing(player)) return;
        float bodyY = AnimSystem.getPlayerBone3DTransform(player, "body", TransformType.POSITION, Vec3f.ZERO).getY();
        // Body motion precedes the player's 15/16 scale; limb offsets follow it.
        matrices.translate(0, bodyY / 15 - .009, 0);
    }
}
