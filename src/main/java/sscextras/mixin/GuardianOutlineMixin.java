package sscextras.mixin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MinecraftClient.class)
public abstract class GuardianOutlineMixin {
    @Inject(method="hasOutline",at=@At("HEAD"),cancellable=true)
    private void guardianOutline(Entity entity,CallbackInfoReturnable<Boolean> cir) {
        if (sscextras.client.GuardianClient.outline(entity)>=0) cir.setReturnValue(true);
    }
}
