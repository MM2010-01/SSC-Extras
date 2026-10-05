package sscextras.mixin;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.client.FeralPerception;

@Mixin(TextRenderer.class)
public abstract class DrakeFeralTextMixin {
    @ModifyVariable(method = "drawLayer(Lnet/minecraft/text/OrderedText;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;II)F",
            at = @At("HEAD"), argsOnly = true)
    private OrderedText sscExtras$gibberish(OrderedText text) { return FeralPerception.scramble(text); }

    @ModifyVariable(method = "drawWithOutline", at = @At("HEAD"), argsOnly = true)
    private OrderedText sscExtras$gibberishOutline(OrderedText text) { return FeralPerception.scramble(text); }

    @Inject(method = "drawInternal(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/font/TextRenderer$TextLayerType;IIZ)I",
            at = @At("HEAD"), cancellable = true)
    private void sscExtras$plainGibberish(String text, float x, float y, int color, boolean shadow, Matrix4f matrix,
            VertexConsumerProvider vertices, TextRenderer.TextLayerType layer, int background, int light, boolean mirror,
            CallbackInfoReturnable<Integer> cir) {
        if (text != null && FeralPerception.active()) cir.setReturnValue(((TextRenderer)(Object)this).draw(
                OrderedText.styledForwardsVisitedString(text, Style.EMPTY), x, y, color, shadow, matrix, vertices, layer, background, light));
    }
}
