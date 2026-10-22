package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.guardian.RuneboundGuardian;
import sscextras.client.GuardianPlayerModel;

@Mixin(PlayerEntityRenderer.class)
public abstract class GuardianModelMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    @Unique private final java.util.Map<net.minecraft.util.Identifier, sscextras.client.AuthoredPlayerModel> sscExtras$rigs = new java.util.HashMap<>();
    @Unique private int sscExtras$rigGeneration = -1;

    @Inject(method="setupTransforms(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;FFF)V",at=@At("HEAD"),cancellable=true)
    private void sscExtras$golemBodyAxis(AbstractClientPlayerEntity player, MatrixStack matrices, float age, float yaw, float delta, CallbackInfo ci) {
        if (!sscextras.golem.DrakeGolem.is(player)) return;
        // Retain native facing, shaking and death; swimming/gliding need no upright-human quarter turn.
        super.setupTransforms(player, matrices, age, yaw, delta);
        if (player.isFallFlying() || player.isSwimming())
            matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotationDegrees(-player.getPitch() * .35f));
        ci.cancel();
    }

    protected GuardianModelMixin(EntityRendererFactory.Context context, PlayerEntityModel<AbstractClientPlayerEntity> model, float shadow) {
        super(context, model, shadow);
    }

    @Unique private sscextras.client.AuthoredPlayerModel sscExtras$rig(AbstractClientPlayerEntity player) {
        if (sscExtras$rigGeneration != sscextras.client.FormPlayerModels.generation()) {
            sscExtras$rigs.clear(); sscExtras$rigGeneration = sscextras.client.FormPlayerModels.generation();
        }
        return sscextras.client.FormPlayerModels.resolve(player, sscExtras$rigs);
    }

    @WrapMethod(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
    private void sscExtras$authoredBody(AbstractClientPlayerEntity player, float yaw, float delta, MatrixStack matrices,
            VertexConsumerProvider buffers, int light, Operation<Void> original) {
        var rig = sscExtras$rig(player);
        if (rig == null) { original.call(player, yaw, delta, matrices, buffers, light); return; }
        var previous = model; model = rig; rig.prepare(player, buffers);
        try { rig.animate(delta); original.call(player, yaw, delta, matrices, buffers, light); }
        finally { model = previous; rig.clear(); }
    }

    @Inject(method = "scale(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("TAIL"))
    private void sscExtras$authoredScale(AbstractClientPlayerEntity player, MatrixStack matrices, float delta, CallbackInfo ci) {
        var rig = sscExtras$rig(player);
        if (rig != null) { float scale = rig.renderScale(); matrices.scale(scale, scale, scale); }
    }

    @WrapMethod(method = "renderArm")
    private void sscExtras$authoredHand(MatrixStack matrices, VertexConsumerProvider buffers, int light,
            AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, Operation<Void> original) {
        var rig = sscExtras$rig(player);
        if (rig == null) { original.call(matrices, buffers, light, player, arm, sleeve); return; }
        boolean left = arm == model.leftArm;
        rig.prepare(player, buffers);
        try { rig.renderHand(matrices, light, left); }
        finally { rig.clear(); }
    }
    @Inject(method = "renderArm", at = @At("TAIL"))
    private void guardianOriginalCuff(MatrixStack matrices, VertexConsumerProvider buffers, int light,
            AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, CallbackInfo ci) {
        if (net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager.getForm(player)
                != net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms.ORIGINAL_SHIFTER
                || sscextras.guardian.GuardianEquipment.equipped(player, "wrist").isEmpty()) return;
        sscextras.client.GuardianEquipmentRenderer.limb(player, arm, arm == ((PlayerEntityRenderer)(Object)this).getModel().leftArm,
                true, matrices, buffers, light);
    }
}
