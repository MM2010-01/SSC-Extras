package sscextras.rituals;

import sscextras.events.AssignedNpcEvent;
import java.util.List;

/** One live staged event; actor policy, persistence and cleanup belong to its adapter. */
public abstract class AbstractRitual<C> extends AssignedNpcEvent {
    protected final RitualStageRunner<C> stages;

    protected AbstractRitual(List<RitualStage<C>> definition, RitualProgress progress) {
        stages = new RitualStageRunner<>(definition, progress);
    }
}
