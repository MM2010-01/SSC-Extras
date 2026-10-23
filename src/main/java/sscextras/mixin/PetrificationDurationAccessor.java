package sscextras.mixin;

import net.minecraft.entity.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StatusEffectInstance.class)
public interface PetrificationDurationAccessor {
    @Accessor("duration") void sscExtras$setPetrificationDuration(int duration);
}
