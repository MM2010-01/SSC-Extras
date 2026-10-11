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
    @Unique private final GuardianPlayerModel[] sscExtras$guardians = new GuardianPlayerModel[4];

    protected GuardianModelMixin(EntityRendererFactory.Context context, PlayerEntityModel<AbstractClientPlayerEntity> model, float shadow) {
        super(context, model, shadow);
    }

    @Unique private GuardianPlayerModel sscExtras$guardian(int stage) {
        if (sscExtras$guardians[stage] == null) sscExtras$guardians[stage] = new GuardianPlayerModel(stage);
        return sscExtras$guardians[stage];
    }

    @WrapMethod(method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V")
    private void sscExtras$guardianBody(AbstractClientPlayerEntity player, float yaw, float delta, MatrixStack matrices,
            VertexConsumerProvider buffers, int light, Operation<Void> original) {
        int stage = RuneboundGuardian.stage(player);
        if (stage < 0) { original.call(player, yaw, delta, matrices, buffers, light); return; }
        var previous = model;
        var guardian = sscExtras$guardian(stage);
        model = guardian;
        guardian.prepare(player, buffers);
        try { original.call(player, yaw, delta, matrices, buffers, light); }
        finally { model = previous; guardian.clear(); }
    }

    @Inject(method = "scale(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("TAIL"))
    private void sscExtras$guardianScale(AbstractClientPlayerEntity player, MatrixStack matrices, float delta, CallbackInfo ci) {
        if (RuneboundGuardian.stage(player) == 3) matrices.scale(1 / .9375f, 1 / .9375f, 1 / .9375f);
    }

    @WrapMethod(method = "renderArm")
    private void sscExtras$guardianHand(MatrixStack matrices, VertexConsumerProvider buffers, int light,
            AbstractClientPlayerEntity player, ModelPart arm, ModelPart sleeve, Operation<Void> original) {
        int stage = RuneboundGuardian.stage(player);
        if (stage < 0) { original.call(matrices, buffers, light, player, arm, sleeve); return; }
        var guardian = sscExtras$guardian(stage);
        guardian.prepare(player, buffers);
        try { guardian.renderHand(matrices, light, arm == model.leftArm); }
        finally { guardian.clear(); }
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
