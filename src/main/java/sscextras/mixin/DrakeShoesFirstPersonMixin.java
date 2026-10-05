package sscextras.mixin;

import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.drake.DrakeShoes;

@Mixin(HeldItemRenderer.class)
public abstract class DrakeShoesFirstPersonMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void sscExtras$mouthHeldItem(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
            float swing, ItemStack stack, float equip, MatrixStack matrices, VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (!stack.isEmpty() && DrakeShoes.hands(player)) ci.cancel();
    }
}
