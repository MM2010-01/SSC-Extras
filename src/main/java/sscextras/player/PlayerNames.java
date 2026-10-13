package sscextras.player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class PlayerNames {
    public record Name(Identifier source, Text text) { }
    private record Provider(Identifier id, int priority, Function<PlayerEntity, Text> resolve) { }
    public interface State {
        Text sscExtras$lastDisplayName();
        void sscExtras$lastDisplayName(Text value);
    }
    private static final Identifier ACCOUNT = new Identifier("minecraft", "account");
    private static final List<Provider> PROVIDERS = new ArrayList<>();
    private PlayerNames() { }

    public static void register(Identifier source, int priority, Function<PlayerEntity, Text> provider) {
        if (PROVIDERS.stream().anyMatch(p -> p.id.equals(source))) throw new IllegalArgumentException("Duplicate player name provider: " + source);
        PROVIDERS.add(new Provider(source, priority, provider));
        PROVIDERS.sort(Comparator.comparingInt(Provider::priority).reversed().thenComparing(p -> p.id.toString()));
    }
    public static Name resolve(PlayerEntity player) {
        for (Provider provider : PROVIDERS) {
            Text text = provider.resolve.apply(player);
            if (text != null && !text.getString().isBlank()) return new Name(provider.id, text.copy());
        }
        return new Name(ACCOUNT, Text.literal(player.getGameProfile().getName()));
    }
    public static Text name(PlayerEntity player, Text original, boolean decorated) {
        var name = resolve(player);
        if (name.source.equals(ACCOUNT)) return original;
        var text = decorated ? Team.decorateName(player.getScoreboardTeam(), name.text.copy()) : name.text.copy();
        return decorated ? text.setStyle(name.text.getStyle().withParent(original.getStyle())) : text;
    }
    public static boolean overridden(PlayerEntity player) { return !resolve(player).source.equals(ACCOUNT); }
    public static void refresh(ServerPlayerEntity player) {
        var state = (State)player;
        Text next = player.getDisplayName();
        if (next.equals(state.sscExtras$lastDisplayName())) return;
        state.sscExtras$lastDisplayName(next.copy());
        player.getServer().getPlayerManager().sendToAll(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, player));
    }
}
