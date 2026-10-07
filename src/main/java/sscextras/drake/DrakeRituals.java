package sscextras.drake;

import net.minecraft.server.network.ServerPlayerEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

public final class DrakeRituals {
    public record Type(String command, BiPredicate<ServerPlayerEntity, DrakeOutpostOwnership.Claim> due,
            Function<DrakeRitualCheckpoint, AbstractRitual> factory) {
        public AbstractRitual create(DrakeRitualCheckpoint checkpoint) { return factory.apply(checkpoint); }
    }
    private static final Map<String, Type> TYPES = new LinkedHashMap<>();
    public static final Type IRON_DRAKE_SHOE_FITTING = register(new Type("irondrakeshoefitting", (p, c) -> c.shoeingDue, ShoeFittingRitual::new));
    public static final Type FERALIZATION = register(new Type("feralization", (p, c) -> DrakeFeralization.due(c), FeralizationRitual::new));
    public static final Type SOULBINDING = register(new Type("soulbinding", (p, c) -> !c.soulbound && c.goodTicks >= DrakeSoulbinding.SERVICE_TICKS, SoulbindingRitual::new));

    private DrakeRituals() { }
    public static Type register(Type type) {
        if (TYPES.putIfAbsent(type.command(), type) != null) throw new IllegalArgumentException("Duplicate ritual " + type.command());
        return type;
    }
    public static List<Type> values() { return List.copyOf(TYPES.values()); }
    public static Type get(String id) { return TYPES.get(id); }
    public static Type due(ServerPlayerEntity player, DrakeOutpostOwnership.Claim claim) {
        return TYPES.values().stream().filter(type -> type.due().test(player, claim)).findFirst().orElse(null);
    }
}
