package sscextras.mixin;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HeldItemFeatureRenderer.class)
public abstract class GuardianHeldItemMixin {
    @Inject(method="render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",at=@At("HEAD"),cancellable=true)
    private void guardianGrip(MatrixStack matrices,VertexConsumerProvider buffers,int light,LivingEntity entity,float limb,float distance,float delta,float age,float yaw,float pitch,CallbackInfo ci) {
        if(entity instanceof PlayerEntity player&&sscextras.guardian.RuneboundGuardian.stage(player)>=0) ci.cancel();
    }
}
