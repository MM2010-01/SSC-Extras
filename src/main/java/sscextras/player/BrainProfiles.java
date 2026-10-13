package sscextras.player;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;

public final class BrainProfiles {
    public record Profile(BiFunction<ServerPlayerEntity, PlayerFormBase, PlayerBrain> factory,
                          Function<PlayerFormBase, Text> name) { }
    private static final Map<Identifier, Profile> FORMS = new HashMap<>(), GROUPS = new HashMap<>();
    private static Profile fallback;
    private BrainProfiles() { }
    public static void registerForm(Identifier id, Profile profile) { add(FORMS, id, profile); }
    public static void registerGroup(Identifier id, Profile profile) { add(GROUPS, id, profile); }
    private static void add(Map<Identifier, Profile> map, Identifier id, Profile profile) {
        if (map.putIfAbsent(id, profile) != null) throw new IllegalArgumentException("Duplicate brain profile: " + id);
    }
    public static void fallback(Profile profile) {
        if (fallback != null) throw new IllegalStateException("Brain fallback already registered");
        fallback = profile;
    }
    public static Profile resolve(PlayerFormBase form) {
        Profile profile = FORMS.get(form.FormID);
        if (profile == null && form.getGroup() != null) profile = GROUPS.get(form.getGroup().GroupID);
        return profile == null ? java.util.Objects.requireNonNull(fallback, "Brain profiles not initialized") : profile;
    }
}
