package sscextras.guardian;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;

public final class GuardianComponents implements EntityComponentInitializer {
    public static final ComponentKey<GuardianState> KEY = ComponentRegistry.getOrCreate(
            new net.minecraft.util.Identifier("ssc-extras", "guardian"), GuardianState.class);
    @Override public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(KEY, GuardianState::new, RespawnCopyStrategy.ALWAYS_COPY);
    }
}
