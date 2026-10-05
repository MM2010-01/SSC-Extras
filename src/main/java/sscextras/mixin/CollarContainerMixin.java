package sscextras.mixin;

import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sscextras.collar.Collars;
import java.util.OptionalInt;

@Mixin(ServerPlayerEntity.class)
public abstract class CollarContainerMixin {
    @Inject(method = "openHandledScreen", at = @At("HEAD"), cancellable = true)
    private void sscExtras$feralCannotOpenContainer(NamedScreenHandlerFactory factory, CallbackInfoReturnable<OptionalInt> cir) {
        if (sscextras.drake.DrakeFeralization.restricted((ServerPlayerEntity)(Object)this)) cir.setReturnValue(OptionalInt.empty());
    }
    @Inject(method = "openHandledScreen", at = @At("RETURN"))
    private void sscExtras$equipFromContainer(NamedScreenHandlerFactory factory, CallbackInfoReturnable<OptionalInt> cir) {
        if (cir.getReturnValue().isPresent()) Collars.openedContainer((ServerPlayerEntity) (Object) this);
    }
}
