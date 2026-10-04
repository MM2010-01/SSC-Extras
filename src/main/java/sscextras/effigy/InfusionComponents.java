package sscextras.effigy;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.util.Identifier;

public final class InfusionComponents implements EntityComponentInitializer {
    public static final ComponentKey<InfusionInventory> KEY = ComponentRegistry.getOrCreate(
            new Identifier("ssc-extras", "infusions"), InfusionInventory.class);

    @Override public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(KEY, InfusionInventory::new, RespawnCopyStrategy.ALWAYS_COPY);
    }
}
