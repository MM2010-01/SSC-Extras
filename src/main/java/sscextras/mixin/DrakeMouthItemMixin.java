package sscextras.mixin;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.features.MouthItemFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import sscextras.drake.DrakeFeralization;
import sscextras.drake.EarthenDrake;

@Mixin(value = MouthItemFeature.class, remap = false)
public abstract class DrakeMouthItemMixin {
    @ModifyVariable(method = "renderItemInMouth", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float sscExtras$shoeingMouthYaw(float yaw, MatrixStack matrices, VertexConsumerProvider vertices,
            int light, LivingEntity entity, ItemStack stack, float headYaw, float headPitch) {
        return entity instanceof PlayerEntity player && sscextras.drake.DrakeSoulbinding.shoeing(player) ? 0 : yaw;
    }

    @ModifyVariable(method = "renderItemInMouth", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float sscExtras$shoeingMouthPitch(float pitch, MatrixStack matrices, VertexConsumerProvider vertices,
            int light, LivingEntity entity, ItemStack stack, float headYaw, float headPitch) {
        if (!(entity instanceof PlayerEntity player) || !sscextras.drake.DrakeSoulbinding.shoeing(player)) return pitch;
        return EarthenDrake.stage(player) == 3 ? .45f * net.minecraft.util.math.MathHelper.DEGREES_PER_RADIAN : 0;
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/onixary/shapeShifterCurseFabric/player_form/PlayerFormBase;getBodyType()Lnet/onixary/shapeShifterCurseFabric/player_form/PlayerFormBodyType;"))
    private PlayerFormBodyType sscExtras$feralMouth(PlayerFormBodyType type, MatrixStack matrices, VertexConsumerProvider vertices,
            int light, LivingEntity entity, float limbAngle, float limbDistance, float tickDelta, float age, float yaw, float pitch) {
        return entity instanceof PlayerEntity player && DrakeFeralization.carriesInMouth(player) ? PlayerFormBodyType.FERAL : type;
    }

    @ModifyExpressionValue(method = "renderItemInMouth", at = @At(value = "INVOKE",
            target = "Lnet/onixary/shapeShifterCurseFabric/util/FeralRenderUtils;isFeralMouthItemBlackListed(Lnet/minecraft/item/ItemStack;)Z", remap = true))
    private boolean sscExtras$carryInMouth(boolean blacklisted, MatrixStack matrices, VertexConsumerProvider vertices,
            int light, LivingEntity entity, ItemStack stack, float yaw, float pitch) {
        return blacklisted && !(entity instanceof PlayerEntity player && DrakeFeralization.carriesInMouth(player));
    }
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
        if (stage < 2) {
            if (stage >= 0 && DrakeFeralization.carriesInMouth(player)) args.set(2, stage == 1 ? -.43 : -.37);
            return;
        }
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
        if (stage < 2) {
            if (stage >= 0 && DrakeFeralization.carriesInMouth(player)) {
                args.set(0, 1.0f); args.set(1, 1.0f); args.set(2, 1.0f);
            }
            return;
        }
        float scale = stage == 2 ? 1.0f : 0.8f;
        args.set(0, scale);
        args.set(1, scale);
        args.set(2, scale);
    }
}
