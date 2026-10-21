package sscextras.petrification;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.entity.LivingEntity;

public final class PetrificationComponents implements EntityComponentInitializer {
    public static final ComponentKey<PetrificationState> KEY = ComponentRegistry.getOrCreate(Petrification.id("petrification"), PetrificationState.class);
    @Override public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerFor(LivingEntity.class, KEY, PetrificationState::new);
        registry.registerForPlayers(KEY, PetrificationState::new, RespawnCopyStrategy.NEVER_COPY);
    }
}
