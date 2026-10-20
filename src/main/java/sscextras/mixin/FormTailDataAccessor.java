package sscextras.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.onixary.shapeShifterCurseFabric.render.form_render.DefaultModelAnimationSystem$tailData", remap = false)
public interface FormTailDataAccessor {
    @Accessor("currentTailDragAmount") float sscExtras$horizontalDrag();
    @Accessor("tailDragAmountVertical") float sscExtras$verticalDrag();
}
