package sscextras.mixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.guardian.GuardianAnchorEntity;
@Mixin(EntityRenderDispatcher.class)
public abstract class GuardianCameraSourceMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void guardianView(Entity entity,double x,double y,double z,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,CallbackInfo ci) {
        if(MinecraftClient.getInstance().options.getPerspective().isFirstPerson()
                && MinecraftClient.getInstance().getCameraEntity() instanceof GuardianAnchorEntity anchor && anchor.follows(entity)) ci.cancel();
    }
}
