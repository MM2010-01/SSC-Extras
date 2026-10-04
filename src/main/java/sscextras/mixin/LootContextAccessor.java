package sscextras.mixin;

import net.minecraft.loot.context.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;

@Mixin(LootContext.class)
public interface LootContextAccessor {
    @Accessor("activeEntries")
    Set<LootContext.Entry<?>> sscExtras$activeEntries();
}
