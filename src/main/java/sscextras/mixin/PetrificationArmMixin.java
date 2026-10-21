package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import sscextras.client.PetrificationClient;
import sscextras.petrification.Petrification;

@Mixin(PlayerEntityRenderer.class)
public abstract class PetrificationArmMixin {
    @WrapOperation(method = "renderArm", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;getSkinTexture()Lnet/minecraft/util/Identifier;"))
    private Identifier petrificationArm(AbstractClientPlayerEntity player, Operation<Identifier> original) {
        var texture = original.call(player);
        return Petrification.replaceTexture(player) ? PetrificationClient.texture(texture) : texture;
    }
}
