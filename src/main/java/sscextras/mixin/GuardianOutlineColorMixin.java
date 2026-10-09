package sscextras.mixin;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Entity.class)
public abstract class GuardianOutlineColorMixin {
    @Inject(method="getTeamColorValue",at=@At("HEAD"),cancellable=true)
    private void guardianColor(CallbackInfoReturnable<Integer> cir) {
        int color=sscextras.client.GuardianClient.outline((Entity)(Object)this);
        if(color>=0) cir.setReturnValue(color);
    }
    @Inject(method="changeLookDirection",at=@At("HEAD"),cancellable=true)
    private void guardianLook(double x,double y,CallbackInfo ci) {
        if(sscextras.client.GuardianClient.look((Entity)(Object)this,x,y)) ci.cancel();
    }
}
