package sscextras.mixin;

import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MobEntity.class)
public interface MobPersistenceAccessor {
    @Accessor("persistent") void sscExtras$persistent(boolean persistent);
}
