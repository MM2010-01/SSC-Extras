package sscextras.events;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** An event with exclusive participants and terminal reservation cleanup. */
public abstract class AssignedNpcEvent extends NpcEvent {
    private EventAssignments assignments;

    protected final boolean reserve(EventAssignments owners, Collection<UUID> participants) {
        if (assignments != null && assignments != owners) throw new IllegalArgumentException("Event cannot change assignment owner");
        if (!owners.acquire(this, participants)) return false;
        assignments = owners;
        return true;
    }
    protected final boolean replaceParticipant(UUID previous, UUID replacement) {
        if (assignments == null || !reserve(assignments, List.of(replacement))) return false;
        if (!previous.equals(replacement)) assignments.release(this, previous);
        return true;
    }
    protected final boolean owns(UUID participant) {
        return assignments != null && assignments.owner(participant) == this;
    }
    protected final void releaseParticipant(UUID participant) {
        if (assignments != null) assignments.release(this, participant);
    }
    @Override protected final void release() {
        try { releaseParticipants(); }
        finally { if (assignments != null) assignments.release(this); }
    }
    protected void releaseParticipants() { }
}
