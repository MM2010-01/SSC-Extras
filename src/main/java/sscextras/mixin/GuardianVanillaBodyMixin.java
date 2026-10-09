package sscextras.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(LivingEntityRenderer.class)
public abstract class GuardianVanillaBodyMixin {
    @WrapOperation(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/model/EntityModel;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"))
    private void guardianBody(EntityModel<?> model,MatrixStack matrices,VertexConsumer consumer,int light,int overlay,float red,float green,float blue,float alpha,
            Operation<Void> original,@Local(argsOnly=true) LivingEntity entity) {
        if(entity instanceof PlayerEntity player&&sscextras.guardian.RuneboundGuardian.stage(player)>=0) return;
        original.call(model,matrices,consumer,light,overlay,red,green,blue,alpha);
    }
}
