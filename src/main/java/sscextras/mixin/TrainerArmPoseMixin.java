package sscextras.mixin;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerEntityRenderer.class)
public abstract class TrainerArmPoseMixin {
    @Inject(method="getArmPose",at=@At("HEAD"),cancellable=true)
    private static void trainerForelegs(AbstractClientPlayerEntity player,Hand hand,CallbackInfoReturnable<BipedEntityModel.ArmPose> ci){
        if(sscextras.trainer.QuadrupedalTrainer.mouth(player))ci.setReturnValue(BipedEntityModel.ArmPose.EMPTY);
    }
}
