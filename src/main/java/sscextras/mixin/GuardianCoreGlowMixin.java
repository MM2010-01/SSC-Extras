package sscextras.mixin;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import sscextras.guardian.GuardianCores;
@Mixin(ItemRenderer.class)
public abstract class GuardianCoreGlowMixin {
    @ModifyVariable(method="renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private int guardianCoreLight(int light,ItemStack stack) {
        if (!stack.isOf(GuardianCores.CORE)) return light;
        return stack.hasNbt() && stack.getNbt().getBoolean("Abandoned") ? Math.max(light, 5 << 4) : LightmapTextureManager.MAX_LIGHT_COORDINATE;
    }
}
