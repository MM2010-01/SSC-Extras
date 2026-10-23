package sscextras.mixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.onixary.shapeShifterCurseFabric.features.MouthItemFeature;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.trainer.QuadrupedalTrainer;

@Mixin(value=MouthItemFeature.class,remap=false)
public abstract class TrainerMouthAttachmentMixin {
    @Shadow @Final private HeldItemRenderer heldItemRenderer;
    @Inject(method="renderItemInMouth",at=@At("HEAD"),cancellable=true)
    private void trainerMouth(MatrixStack matrices,VertexConsumerProvider buffers,int light,LivingEntity entity,ItemStack stack,float yaw,float pitch,CallbackInfo ci){
        if(!(entity instanceof net.minecraft.client.network.AbstractClientPlayerEntity player)||!QuadrupedalTrainer.mouth(player)||sscextras.player.PlayerMouth.withoutTrainer(player)
                ||!(MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerEntityRenderer renderer))return;
        var model=renderer.getModel();
        if(model instanceof sscextras.client.AuthoredPlayerModel)return;
        matrices.push();model.head.rotate(matrices);matrices.translate(.06,-.10,-.35);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180));
        heldItemRenderer.renderItem(entity,stack,ModelTransformationMode.GROUND,false,matrices,buffers,light);
        matrices.pop();ci.cancel();
    }
}
