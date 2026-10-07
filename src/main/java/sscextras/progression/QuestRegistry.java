package sscextras.progression;

import net.minecraft.util.Identifier;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QuestRegistry {
    private static final Map<Identifier, QuestDefinition> DEFINITIONS = new LinkedHashMap<>();
    private static int revision;
    private QuestRegistry() { }
    public static QuestDefinition register(QuestDefinition definition) {
        if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null) throw new IllegalArgumentException("Duplicate quest " + definition.id());
        revision++; return definition;
    }
    public static QuestDefinition get(Identifier id) { return DEFINITIONS.get(id); }
    static int revision() { return revision; }
}
