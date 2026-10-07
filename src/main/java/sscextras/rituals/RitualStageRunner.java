package sscextras.rituals;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RitualStageRunner<C> {
    public enum Result { RUNNING, PAUSED, COMPLETE, CANCELLED }
    private final List<RitualStage<C>> stages;
    private final RitualProgress progress;
    private boolean cancelled;

    public RitualStageRunner(List<RitualStage<C>> stages, RitualProgress progress) {
        this.stages = List.copyOf(stages); this.progress = progress;
        if (stages.isEmpty()) throw new IllegalArgumentException("Empty ritual");
        Set<String> ids = new HashSet<>();
        for (var stage : stages) if (!ids.add(stage.id())) throw new IllegalArgumentException("Duplicate ritual stage " + stage.id());
        for (var stage : stages) for (var fallback : List.of(stage.interrupted(), stage.timedOut()))
            if (fallback.response() == RitualStage.Response.GO_TO && !ids.contains(fallback.stage()))
                throw new IllegalArgumentException("Unknown fallback stage " + fallback.stage());
        if (progress.stage.isEmpty()) progress.stage = stages.get(0).id();
        if (!ids.contains(progress.stage) || !ids.containsAll(progress.completed)) throw new IllegalArgumentException("Unknown saved ritual stage");
        progress.elapsed = Math.min(progress.elapsed, current().duration());
    }

    public RitualStage<C> current() { return stages.get(index()); }
    private int index() {
        for (int i = 0; i < stages.size(); i++) if (stages.get(i).id().equals(progress.stage)) return i;
        throw new IllegalStateException("Missing ritual stage");
    }
    public int timeline() {
        int ticks = progress.elapsed;
        for (int i = 0; i < index(); i++) ticks += stages.get(i).duration();
        return ticks;
    }
    public void resume() { progress.waiting = 0; cancelled = false; }

    public Result tick(C context, int ticks, boolean ready) {
        if (ticks <= 0) throw new IllegalArgumentException("Nonpositive ritual tick delta");
        if (cancelled) return Result.CANCELLED;
        if (progress.finished) return Result.COMPLETE;
        var stage = current();
        progress.waiting = (int)Math.min(Integer.MAX_VALUE, (long)progress.waiting + ticks);
        if (!ready) {
            if (progress.waiting >= stage.timeout()) return fallback(stage.timedOut());
            return fallback(stage.interrupted());
        }
        progress.elapsed = (int)Math.min(stage.duration(), (long)progress.elapsed + ticks);
        if (stage.ready(context, progress.elapsed) && (progress.completed.contains(stage.id()) || stage.commit().test(context))) {
            progress.completed.add(stage.id());
            int next = index() + 1;
            if (next == stages.size()) { progress.finished = true; return Result.COMPLETE; }
            progress.stage = stages.get(next).id(); progress.elapsed = progress.waiting = 0;
            return Result.RUNNING;
        }
        return progress.waiting >= stage.timeout() ? fallback(stage.timedOut()) : Result.RUNNING;
    }

    private Result fallback(RitualStage.Fallback fallback) {
        switch (fallback.response()) {
            case PAUSE -> { }
            case RESTART_STAGE -> progress.elapsed = 0;
            case GO_TO -> { progress.stage = fallback.stage(); progress.elapsed = 0; }
            case CANCEL -> { cancelled = true; return Result.CANCELLED; }
        }
        return Result.PAUSED;
    }
}
