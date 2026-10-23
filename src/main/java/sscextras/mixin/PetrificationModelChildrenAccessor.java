package sscextras.mixin;

import java.util.Map;
import net.minecraft.client.model.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelPart.class)
public interface PetrificationModelChildrenAccessor {
    @Accessor("children") Map<String, ModelPart> petrificationChildren();
}
