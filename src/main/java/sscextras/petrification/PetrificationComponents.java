package sscextras.petrification;

import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry;
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer;
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy;
import net.minecraft.entity.LivingEntity;

public final class PetrificationComponents implements EntityComponentInitializer {
    public static final ComponentKey<PetrificationState> KEY = ComponentRegistry.getOrCreate(Petrification.id("petrification"), PetrificationState.class);
    public static final net.minecraft.util.Identifier SYNC_REQUEST = Petrification.id("petrification_sync");
    public static void registerSync() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(SYNC_REQUEST, (server, player, handler, buffer, response) -> {
            if (buffer.readableBytes() < 1 || buffer.readableBytes() > 5) return;
            int id = buffer.readVarInt();
            server.execute(() -> {
                if (!(player.getWorld().getEntityById(id) instanceof LivingEntity living)) return;
                if (living != player && !net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(living).contains(player)) return;
                if (living.hasStatusEffect(Petrification.EFFECT))
                    KEY.syncWith(player, (dev.onyxstudios.cca.api.v3.component.ComponentProvider)living);
            });
        });
    }
    @Override public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerFor(LivingEntity.class, KEY, PetrificationState::new);
        registry.registerForPlayers(KEY, PetrificationState::new, RespawnCopyStrategy.NEVER_COPY);
    }
}
