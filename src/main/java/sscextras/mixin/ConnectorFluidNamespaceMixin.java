package sscextras.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import java.util.Optional;

@Pseudo
@Mixin(targets = "org.sinytra.connector.mod.compat.FluidHandlerCompat", remap = false)
public abstract class ConnectorFluidNamespaceMixin {
    @WrapOperation(method = "initFabricFluidTypes", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fml/ModList;getModContainerById(Ljava/lang/String;)Ljava/util/Optional;"))
    private static Optional<?> sscExtras$fluidOwner(@Coerce Object mods, String namespace, Operation<Optional<?>> original) {
        return original.call(mods, namespace.equals("ssc-extras") ? "ssc_extras" : namespace);
    }
}
