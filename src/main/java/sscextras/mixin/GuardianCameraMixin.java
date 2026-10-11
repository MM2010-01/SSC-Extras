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
    @Inject(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/Camera;setPos(DDD)V",ordinal=0,shift=At.Shift.AFTER))
    private void guardianCamera(BlockView world,Entity entity,boolean thirdPerson,boolean inverse,float delta,CallbackInfo ci) {
        if(!GuardianClient.camera()) return;
        setRotation(GuardianClient.yaw,GuardianClient.pitch);
        if (GuardianClient.detachedCamera()) setPos(entity.getLerpedPos(delta).add(0, .35, 0));
    }
    @ModifyArg(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/Camera;clipToSpace(D)D"),index=0)
    private double guardianDistance(double distance) {
        return GuardianClient.detachedCamera() ? 3 : distance;
    }
}
