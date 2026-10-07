package sscextras.progression;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

public record QuestObjective(String id, Identifier metric, long required, Predicate<ProgressEvent> accepts) {
    public QuestObjective {
        if (id == null || id.isBlank() || required <= 0) throw new IllegalArgumentException("Invalid quest objective");
        Objects.requireNonNull(metric); Objects.requireNonNull(accepts);
    }
    public static QuestObjective count(String id, Identifier metric, long required) {
        return new QuestObjective(id, metric, required, event -> true);
    }
    public static QuestObjective withActorAt(String id, Identifier metric, long required, UUID actor, GlobalPos destination, double radius) {
        Objects.requireNonNull(actor); Objects.requireNonNull(destination);
        if (!Double.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("Invalid destination radius");
        return new QuestObjective(id, metric, required, event -> actor.equals(event.actor())
                && destination.getDimension().equals(event.dimension())
                && event.position().squaredDistanceTo(Vec3d.ofCenter(destination.getPos())) <= radius * radius);
    }
}
