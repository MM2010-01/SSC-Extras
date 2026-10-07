package sscextras.progression;

import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.Objects;
import java.util.UUID;

public record ProgressEvent(Identifier metric, long amount, UUID actor, RegistryKey<World> dimension, Vec3d position) {
    public ProgressEvent {
        Objects.requireNonNull(metric); Objects.requireNonNull(dimension); Objects.requireNonNull(position);
        if (amount <= 0) throw new IllegalArgumentException("Progress must be positive");
    }
}
