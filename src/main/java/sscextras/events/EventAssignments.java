package sscextras.events;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public final class EventAssignments {
    private final Map<UUID, NpcEvent> owners = new HashMap<>();

    public boolean acquire(NpcEvent event, Collection<UUID> participants) {
        if (event.status() == NpcEvent.Status.FINISHED) return false;
        var unique = new HashSet<>(participants);
        if (unique.size() != participants.size()) return false;
        for (var id : unique) if (owners.containsKey(id) && owners.get(id) != event) return false;
        unique.forEach(id -> owners.put(id, event));
        return true;
    }

    public NpcEvent owner(UUID participant) { return owners.get(participant); }
    public void release(NpcEvent event) { owners.values().removeIf(owner -> owner == event); }
    public void release(NpcEvent event, UUID participant) { owners.remove(participant, event); }
}
