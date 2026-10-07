package sscextras.rituals;

import java.util.Objects;
import java.util.function.Predicate;

public record RitualStage<C>(String id, int duration, Predicate<C> proceed,
        Completion completion, int timeout, Fallback interrupted, Fallback timedOut,
        Predicate<C> commit) {
    public enum Completion { AFTER, WHEN, AFTER_AND_WHEN, AFTER_OR_WHEN }
    public enum Response { PAUSE, RESTART_STAGE, GO_TO, CANCEL }
    public record Fallback(Response response, String stage) {
        public static Fallback pause() { return new Fallback(Response.PAUSE, ""); }
        public static Fallback restart() { return new Fallback(Response.RESTART_STAGE, ""); }
        public static Fallback goTo(String stage) { return new Fallback(Response.GO_TO, stage); }
        public static Fallback cancel() { return new Fallback(Response.CANCEL, ""); }
        public Fallback { Objects.requireNonNull(response); Objects.requireNonNull(stage); }
    }

    public RitualStage {
        if (id == null || id.isBlank() || duration < 0 || timeout <= 0) throw new IllegalArgumentException("Invalid ritual stage");
        Objects.requireNonNull(proceed); Objects.requireNonNull(completion);
        Objects.requireNonNull(interrupted); Objects.requireNonNull(timedOut); Objects.requireNonNull(commit);
    }

    public static <C> RitualStage<C> afterAndWhen(String id, int duration, Predicate<C> proceed, Predicate<C> commit) {
        return new RitualStage<>(id, duration, proceed, Completion.AFTER_AND_WHEN,
                12000, Fallback.pause(), Fallback.cancel(), commit);
    }

    public boolean ready(C context, int elapsed) {
        return switch (completion) {
            case AFTER -> elapsed >= duration;
            case WHEN -> proceed.test(context);
            case AFTER_AND_WHEN -> elapsed >= duration && proceed.test(context);
            case AFTER_OR_WHEN -> elapsed >= duration || proceed.test(context);
        };
    }
}
