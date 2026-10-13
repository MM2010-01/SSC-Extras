package sscextras.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.client.MentalPerception;

@Mixin(ItemRenderer.class)
public abstract class MentalInstrumentMixin {
    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void sscExtras$unreadableDial(ItemStack stack, World world, LivingEntity entity, int seed, CallbackInfoReturnable<BakedModel> cir) {
        if ((stack.isOf(Items.CLOCK) || stack.isOf(Items.COMPASS) || stack.isOf(Items.RECOVERY_COMPASS)) && MentalPerception.unreadable(stack))
            cir.setReturnValue(MinecraftClient.getInstance().getItemRenderer().getModels().getModel(stack));
    }
}
