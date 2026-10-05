package sscextras.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.client.DrakeCameraControl;

@Pseudo
@Mixin(targets = "com.github.leawind.thirdperson.ThirdPerson", remap = false)
public abstract class DrakeLeawindCameraMixin {
    @Inject(method = "isAvailable", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sscExtras$ritualCamera(CallbackInfoReturnable<Boolean> cir) {
        if (DrakeCameraControl.free(MinecraftClient.getInstance().player)) cir.setReturnValue(false);
    }
}
