package sscextras.mixin;

import java.util.HashMap;
import java.util.UUID;
import net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DefaultModelAnimationSystem.class, remap = false)
public interface FormTailStateAccessor {
    @Accessor("tailDataMap")
    static HashMap<UUID, ?> sscExtras$tails() { throw new AssertionError(); }
}
