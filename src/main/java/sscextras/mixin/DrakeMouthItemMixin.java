package sscextras.mixin;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.onixary.shapeShifterCurseFabric.features.MouthItemFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import sscextras.drake.EarthenDrake;

@Mixin(value = MouthItemFeature.class, remap = false)
public abstract class DrakeMouthItemMixin {
    @ModifyArgs(method = "renderItemInMouth", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V", ordinal = 0, remap = true))
    private void sscExtras$headPivot(Args args, MatrixStack matrices, VertexConsumerProvider vertices,
            int light, LivingEntity entity, ItemStack stack, float yaw, float pitch) {
        if (!(entity instanceof PlayerEntity player)) return;
        int stage = EarthenDrake.stage(player);
        if (stage != 3) return;
        args.set(1, (float)args.get(1) + 0.66f);
        args.set(2, (float)args.get(2) - 0.453333f);
    }

    @ModifyArgs(method = "renderItemInMouth", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;translate(DDD)V", ordinal = 0, remap = true))
    private void sscExtras$snoutPosition(Args args, MatrixStack matrices, VertexConsumerProvider vertices,
            int light, LivingEntity entity, ItemStack stack, float yaw, float pitch) {
        if (!(entity instanceof PlayerEntity player)) return;
        int stage = EarthenDrake.stage(player);
        if (stage < 2) return;
        args.set(0, stage == 2 ? 0.0625 : 0.05);
        args.set(1, stage == 2 ? 0.04 : 0.107);
        args.set(2, stage == 2 ? -0.47 : -0.372);
    }

    @ModifyArgs(method = "renderItemInMouth", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V", ordinal = 0, remap = true))
    private void sscExtras$mouthItemScale(Args args, MatrixStack matrices, VertexConsumerProvider vertices,
            int light, LivingEntity entity, ItemStack stack, float yaw, float pitch) {
        if (!(entity instanceof PlayerEntity player)) return;
        int stage = EarthenDrake.stage(player);
        if (stage < 2) return;
        float scale = stage == 2 ? 1.0f : 0.8f;
        args.set(0, scale);
        args.set(1, scale);
        args.set(2, scale);
    }
}
