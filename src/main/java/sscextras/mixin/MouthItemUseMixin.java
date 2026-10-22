package sscextras.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;
import sscextras.player.PlayerMouth;
import sscextras.trainer.QuadrupedalTrainer;

@Mixin(ItemStack.class)
public abstract class MouthItemUseMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void mouthUse(World world, PlayerEntity player, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        var stack = (ItemStack)(Object)this;
        if (PlayerMouth.preventsUse(player, stack) || hand == Hand.OFF_HAND && QuadrupedalTrainer.mouth(player)) {
            if (!world.isClient) player.sendMessage(Text.translatable("message.ssc-extras.mouth.no_ranged"), true);
            cir.setReturnValue(TypedActionResult.fail(stack));
        }
    }
    @Inject(method = {"onStoppedUsing", "usageTick"}, at = @At("HEAD"), cancellable = true)
    private void mouthStopChargedWeapon(World world, LivingEntity entity, int remaining, CallbackInfo ci) {
        if (entity instanceof PlayerEntity player && PlayerMouth.preventsUse(player, (ItemStack)(Object)this)) {
            player.clearActiveItem(); ci.cancel();
        }
    }
}
