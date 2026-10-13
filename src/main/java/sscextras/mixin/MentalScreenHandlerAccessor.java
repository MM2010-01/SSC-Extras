package sscextras.mixin;

import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ScreenHandler.class)
public interface MentalScreenHandlerAccessor {
    @Accessor("type") ScreenHandlerType<?> sscExtras$nullableType();
}
