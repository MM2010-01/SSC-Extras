package sscextras.player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;

/** Form policy only. PlayerMind remains the sole owner of points, saves and synchronization. */
public final class MentalDecay {
    private static final Map<Identifier, Map<Identifier, Double>> RULES = new HashMap<>();
    private MentalDecay() { }

    /** Register once during common initialization, for an exact form/stage and a completed action. */
    public static void register(Identifier form, Identifier action, double points) {
        Objects.requireNonNull(form); Objects.requireNonNull(action);
        if (!Double.isFinite(points) || points < .01 || points > PlayerMind.MAX_POINTS)
            throw new IllegalArgumentException("INT loss must be between 0.01 and " + PlayerMind.MAX_POINTS);
        double rounded = Math.round(points * 100) / 100.0;
        if (RULES.computeIfAbsent(form, ignored -> new HashMap<>()).putIfAbsent(action, rounded) != null)
            throw new IllegalArgumentException("Duplicate INT loss rule: " + form + " / " + action);
    }

    /** Call once after the action succeeds on the server, never from a permission check or renderer. */
    public static double onAction(ServerPlayerEntity player, Identifier action) {
        if (RULES.isEmpty()) return 0;
        var rules = RULES.get(FormAbilityManager.getForm(player).FormID);
        if (rules == null) return 0;
        Double cost = rules.get(action);
        return cost == null ? 0 : PlayerMind.losePoints(player, cost);
    }
}
