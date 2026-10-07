package sscextras.events;

import java.util.UUID;

public abstract class NpcEvent {
    public enum Status { RUNNING, PAUSED, FINISHED }
    private final UUID id = UUID.randomUUID();
    private Status status = Status.PAUSED;

    public final UUID id() { return id; }
    public final Status status() { return status; }
    public final void resume() { if (status != Status.FINISHED) status = Status.RUNNING; }
    public final void pause() { if (status != Status.FINISHED) status = Status.PAUSED; }
    public final void finish() {
        if (status == Status.FINISHED) return;
        status = Status.FINISHED;
        release();
    }
    protected abstract void release();
}
