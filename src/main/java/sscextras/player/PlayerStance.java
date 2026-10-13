package sscextras.player;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.ability.FormAbilityManager;

public final class PlayerStance {
    public enum Stance { FORM_DEFAULT, UPRIGHT, QUADRUPEDAL }
    public record Snapshot(Stance stance, java.util.Set<Identifier> sources, net.minecraft.entity.EntityDimensions dimensions,
                           float eyeHeight, float stepHeight, Profile profile) { }
    public interface Profile {
        boolean supports(PlayerEntity player, Stance stance);
        Stance current(PlayerEntity player);
        void refresh(ServerPlayerEntity player);
        default boolean humanoidPose() { return false; }
        default java.util.Set<Identifier> sources(PlayerEntity player) { return java.util.Set.of(); }
    }
    public interface State {
        Runtime sscExtras$stance();
        boolean sscExtras$quadrupedRequest();
        void sscExtras$quadrupedRequest(boolean value);
    }
    public static final class Runtime { private final Map<Identifier, Request> requests = new LinkedHashMap<>(); }
    public static final class Request implements AutoCloseable {
        private final ServerPlayerEntity player;
        private final Identifier source;
        private Request(ServerPlayerEntity player, Identifier source) { this.player = player; this.source = source; }
        public boolean active() { return state(player).sscExtras$stance().requests.get(source) == this; }
        @Override public void close() {
            if (active()) { state(player).sscExtras$stance().requests.remove(source); refresh(player); }
        }
    }
    private static final Map<Identifier, Profile> FORMS = new HashMap<>(), GROUPS = new HashMap<>();
    private static final Profile NATIVE = new Profile() {
        public boolean supports(PlayerEntity player, Stance stance) { return stance == Stance.FORM_DEFAULT; }
        public Stance current(PlayerEntity player) { return Stance.FORM_DEFAULT; }
        public void refresh(ServerPlayerEntity player) { player.calculateDimensions(); }
    };
    private static final Profile HUMANOID = new Profile() {
        public boolean supports(PlayerEntity player, Stance stance) { return true; }
        public Stance current(PlayerEntity player) {
            return requested(player) || sscextras.drake.DrakeFeralization.permanent(player) ? Stance.QUADRUPEDAL : Stance.UPRIGHT;
        }
        public void refresh(ServerPlayerEntity player) { player.calculateDimensions(); }
        public boolean humanoidPose() { return true; }
        public java.util.Set<Identifier> sources(PlayerEntity player) {
            return sscextras.drake.DrakeFeralization.permanent(player) ? java.util.Set.of(new Identifier("ssc-extras", "feral_body")) : java.util.Set.of();
        }
    };
    private PlayerStance() { }
    private static State state(PlayerEntity player) { return (State)player; }
    public static void registerForm(Identifier id, Profile profile) { add(FORMS, id, profile); }
    public static void registerGroup(Identifier id, Profile profile) { add(GROUPS, id, profile); }
    private static void add(Map<Identifier, Profile> map, Identifier id, Profile profile) {
        if (map.putIfAbsent(id, profile) != null) throw new IllegalArgumentException("Duplicate stance profile: " + id);
    }
    public static Profile profile(PlayerEntity player) {
        if (state(player).sscExtras$stance() == null) return NATIVE;
        var form = FormAbilityManager.getForm(player);
        Profile profile = FORMS.get(form.FormID);
        if (profile == null && form.getGroup() != null) profile = GROUPS.get(form.getGroup().GroupID);
        if (profile != null) return profile;
        // SSC's ordinary humanoid rig already has the shared four-limb pose adapter.
        return form.getBodyType() == PlayerFormBodyType.NORMAL && sscextras.guardian.RuneboundGuardian.stage(player) < 0 ? HUMANOID : NATIVE;
    }
    public static Stance current(PlayerEntity player) { return profile(player).current(player); }
    public static Snapshot snapshot(PlayerEntity player) {
        var profile = profile(player);
        var sources = new java.util.LinkedHashSet<>(profile.sources(player));
        if (!player.getWorld().isClient) sources.addAll(state(player).sscExtras$stance().requests.keySet());
        else if (requested(player)) sources.add(new Identifier("ssc-extras", "stance_request"));
        return new Snapshot(profile.current(player), java.util.Set.copyOf(sources), player.getDimensions(player.getPose()),
                player.getEyeHeight(player.getPose()), player.getStepHeight(), profile);
    }
    public static boolean quadrupedal(PlayerEntity player) { return current(player) == Stance.QUADRUPEDAL; }
    public static boolean genericQuadruped(PlayerEntity player) { return profile(player).humanoidPose() && quadrupedal(player); }
    public static boolean supports(PlayerEntity player, Stance stance) { return profile(player).supports(player, stance); }
    public static boolean requested(PlayerEntity player) { return state(player).sscExtras$quadrupedRequest(); }
    public static Request request(ServerPlayerEntity player, Identifier source, Stance stance) {
        if (stance != Stance.QUADRUPEDAL || !supports(player, stance)) return null;
        var request = new Request(player, source);
        state(player).sscExtras$stance().requests.put(source, request);
        refresh(player);
        return request;
    }
    public static void refresh(ServerPlayerEntity player) {
        var value = state(player);
        value.sscExtras$quadrupedRequest(!value.sscExtras$stance().requests.isEmpty() && supports(player, Stance.QUADRUPEDAL));
        profile(player).refresh(player);
    }
    public static void clear(ServerPlayerEntity player) {
        state(player).sscExtras$stance().requests.clear();
        refresh(player);
    }
    public static void formChanged(ServerPlayerEntity player) {
        if (!supports(player, Stance.QUADRUPEDAL)) state(player).sscExtras$stance().requests.clear();
        refresh(player);
    }
}
