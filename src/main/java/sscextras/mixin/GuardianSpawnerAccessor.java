package sscextras.mixin;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.spawner.Spawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(ServerWorld.class)
public interface GuardianSpawnerAccessor {
    @Accessor("spawners") List<Spawner> sscExtras$spawners();
}
