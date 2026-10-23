package sscextras.mixin;

import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Forward the shared P press to vanilla on loaders whose lookup only queues one binding. */
@Mixin(KeyBinding.class)
public interface GolemStatueKeyAccessor {
    @Accessor("timesPressed") int sscExtras$queuedPresses();
    @Accessor("timesPressed") void sscExtras$queuedPresses(int count);
}
