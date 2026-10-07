package sscextras.progression;

import net.minecraft.util.Identifier;
import java.util.List;
import java.util.Objects;

public record QuestDefinition(Identifier id, List<QuestObjective> objectives, long rewardPoints) {
    public QuestDefinition {
        Objects.requireNonNull(id); objectives = List.copyOf(objectives);
        if (objectives.isEmpty() || rewardPoints < 0
                || objectives.stream().map(QuestObjective::id).distinct().count() != objectives.size())
            throw new IllegalArgumentException("Quest requires unique objectives and a nonnegative reward");
    }
}
