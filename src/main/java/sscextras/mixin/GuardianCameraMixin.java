package sscextras.mixin;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sscextras.client.GuardianClient;
@Mixin(Camera.class)
public abstract class GuardianCameraMixin {
    @Shadow protected abstract void setPos(Vec3d pos);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Inject(method="update",at=@At("TAIL"))
    private void guardianCamera(BlockView world,Entity entity,boolean thirdPerson,boolean inverse,float delta,CallbackInfo ci) {
        if(!GuardianClient.camera()) return;
        if(!GuardianClient.coreCamera()) setPos(GuardianClient.ritualCamera(delta));
        setRotation(GuardianClient.yaw,GuardianClient.coreCamera()?GuardianClient.pitch:15);
    }
}
